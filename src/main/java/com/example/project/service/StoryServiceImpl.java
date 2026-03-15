package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.story.StoryDTO;
import com.example.project.dto.count.StoryReactionCountDTO;
import com.example.project.dto.profile.SubscriberDTO;
import com.example.project.dto.user.UserResponseDTO;
import com.example.project.entity.Story;
import com.example.project.entity.StoryReaction;
import com.example.project.entity.StoryViewer;
import com.example.project.enums.Reactions;
import com.example.project.enums.StoryCategory;
import com.example.project.enums.StoryMood;
import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.ImaggaService;
import com.example.project.interfaces.StoryCrudService;
import com.example.project.interfaces.StoryReactionService;
import com.example.project.metrics.HomeRabbitMetricsService;
import com.example.project.metrics.StoryMetricsService;
import com.example.project.repository.StoryReactionRepository;
import com.example.project.repository.StoryRepository;
import com.example.project.repository.StoryViewerRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Builder
@AllArgsConstructor
public class StoryServiceImpl implements StoryCrudService, StoryReactionService {

    private static final long STORY_LIFETIME_HOURS = 24;

    private final AuthClient authClient;
    private final SubscriptionClient subscriptionClient;
    private final StoryRepository storyRepository;
    private final StoryViewerRepository storyViewerRepository;
    private final StoryReactionRepository storyReactionRepository;
    private final ImaggaService imaggaService;
    private final NotificationProducer notificationProducer;
    private final StoryMetricsService storyMetrics;
    private final HomeRabbitMetricsService rabbitMetrics;

    @Override
    @Transactional
    public Story createStory(StoryDTO storyDTO) throws Exception {
        log.info("Создание новой истории");

        return storyMetrics.createStoryTimer().recordCallable(() -> {
            UserResponseDTO currentUser = authClient.getCurrentUser();
            UUID userId = UUID.fromString(currentUser.getId());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new UnauthorizedException("JWT истёк");
            }

            List<String> tags = new ArrayList<>();
            String category = StoryCategory.OTHER.name();
            String mood = StoryMood.NEUTRAL.name();

            if (storyDTO.getPhotoBytes() != null && storyDTO.getPhotoFileName() != null) {
                try {
                    tags = imaggaService.extractTagsFromBytes(storyDTO.getPhotoBytes(), storyDTO.getPhotoFileName());
                    List<String> colors = imaggaService.extractColorsFromBytes(storyDTO.getPhotoBytes(), storyDTO.getPhotoFileName());
                    category = mapTagsToCategory(tags);
                    mood = mapColorsToMood(colors);
                } catch (Exception e) {
                    log.warn("Imagga не смог сгенерировать теги/цвета: {}", e.getMessage());
                }
            }

            Story story = Story.builder()
                    .userId(userId)
                    .description(storyDTO.getDescription())
                    .photoUrl(storyDTO.getPhotoUrl())
                    .createdAt(LocalDateTime.now())
                    .expireAt(LocalDateTime.now().plusHours(STORY_LIFETIME_HOURS))
                    .tags(tags.isEmpty() ? storyDTO.getTags() : tags)
                    .category(StoryCategory.valueOf(category))
                    .location(storyDTO.getLocation())
                    .mood(StoryMood.valueOf(mood))
                    .overlayText(storyDTO.getOverlayText())
                    .musicCaption(storyDTO.getMusicCaption())
                    .isPublic(storyDTO.isPublic())
                    .viewers(new ArrayList<>())
                    .build();

            Story savedStory = storyRepository.save(story);
            storyMetrics.incrementCreated();

            try {
                List<UUID> subscriberIds = subscriptionClient.getFollowers(userId)
                        .stream()
                        .map(SubscriberDTO::getSubscriberUserId)
                        .toList();

                for (UUID subscriberId : subscriberIds) {
                    notificationProducer.sendStoryCreated(
                            savedStory.getId().toString(),
                            userId.toString(),
                            subscriberId.toString(),
                            currentUser.getUsername() != null ? currentUser.getUsername() : userId.toString()
                    );
                    rabbitMetrics.increment();
                }
                log.info("Отправлено {} уведомлений о новой истории", subscriberIds.size());
            } catch (Exception e) {
                log.warn("Не удалось отправить уведомления о story: {}", e.getMessage());
            }

            log.info("✅ История успешно создана для пользователя с ID: {}", userId);
            return savedStory;
        });
    }

    @Override
    public void deleteStory(UUID storyId) {
        log.info("Удаление истории с ID: {}", storyId);
        storyRepository.findById(storyId)
                .orElseThrow(() -> {
                    log.warn("❌ История с ID: {} не найдена", storyId);
                    return new StoryNotFoundException("История с ID " + storyId + " не найдена");
                });
        storyRepository.deleteById(storyId);
        storyMetrics.incrementDeleted();
    }

    @Override
    public void viewStory(UUID storyId, UUID viewerId) {
        log.info("Пользователь {} просматривает историю {}", viewerId, storyId);
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("История с ID " + storyId + " не найдена"));

        if (story.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new StoryIsNotAviableByTimeException("История истекла");
        }
        if (storyViewerRepository.existsByStoryIdAndViewerId(storyId, viewerId)) {
            return;
        }

        StoryViewer viewer = new StoryViewer();
        viewer.setStory(story);
        viewer.setViewerId(viewerId);
        viewer.setViewedAt(LocalDateTime.now());
        storyViewerRepository.save(viewer);
        storyMetrics.incrementViewed();

        // Уведомление автору истории
        try {
            if (!story.getUserId().equals(viewerId)) {
                notificationProducer.sendStoryViewed(
                        storyId.toString(),
                        viewerId.toString(),
                        story.getUserId().toString(),
                        viewerId.toString()
                );
                rabbitMetrics.increment();
            }
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомление о просмотре story: {}", e.getMessage());
        }
    }

    @Override
    public boolean hasViewed(UUID storyId, UUID viewerId) {
        return storyViewerRepository.existsByStoryIdAndViewerId(storyId, viewerId);
    }

    @Override
    public long countViews(UUID storyId) {
        return storyViewerRepository.countByStoryId(storyId);
    }

    @Transactional
    public void reactToStory(UUID storyId, Reactions reaction) {
        UserResponseDTO currentUser = authClient.getCurrentUser();
        UUID userId = UUID.fromString(currentUser.getId());

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("Story not found"));

        Optional<StoryReaction> existing = storyReactionRepository.findByStoryIdAndUserId(storyId, userId);

        if (existing.isPresent()) {
            existing.get().setReaction(reaction);
            existing.get().setReactedAt(LocalDateTime.now());
            storyMetrics.incrementReactionAdded();
            return;
        }

        StoryReaction storyReaction = StoryReaction.builder()
                .story(story)
                .userId(userId)
                .reaction(reaction)
                .reactedAt(LocalDateTime.now())
                .build();
        storyReactionRepository.save(storyReaction);
        storyMetrics.incrementReactionAdded();
    }

    @Override
    public void deleteReaction(UUID storyId) {
        UserResponseDTO currentUser = authClient.getCurrentUser();
        UUID userId = UUID.fromString(currentUser.getId());

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("История с ID " + storyId + " не найдена"));

        if (story.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new StoryIsNotAviableByTimeException("История истекла");
        }

        StoryReaction existing = storyReactionRepository.findByStoryIdAndUserId(storyId, userId)
                .orElseThrow(() -> new StoryNotFoundException("Реакция не найдена"));

        storyReactionRepository.delete(existing);
        storyMetrics.incrementReactionDeleted();
    }

    @Override
    public List<StoryReactionCountDTO> getReactionStats(UUID storyId) {
        return storyReactionRepository.countReactionsByStory(storyId)
                .stream()
                .map(r -> new StoryReactionCountDTO((Reactions) r[0], (Long) r[1]))
                .toList();
    }

    @Override
    public List<Story> getAllActiveStories() {
        return storyRepository.findByExpireAtAfter(LocalDateTime.now());
    }

    @Override
    public List<Story> getStoriesByUserId(UUID userId) {
        return storyRepository.findByUserIdAndExpireAtAfterOrderByCreatedAtDesc(userId, LocalDateTime.now());
    }

    @Override
    public Story getStoryById(UUID storyId) {
        return storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("История с ID " + storyId + " не найдена"));
    }

    @Override
    @Transactional
    public void setAllStoriesPrivacy(UUID userId, boolean isPublic) {
        List<Story> stories = storyRepository.findByUserIdAndExpireAtAfterOrderByCreatedAtDesc(userId, LocalDateTime.now());
        stories.forEach(s -> s.setPublic(isPublic));
        storyRepository.saveAll(stories);
    }

    public List<Story> getAllStories() {
        return storyRepository.findByExpireAtAfterAndIsPublicTrue(LocalDateTime.now());
    }

    // ── helpers ──────────────────────────────────────────

    private String mapTagsToCategory(List<String> tags) {
        if (tags == null) return StoryCategory.OTHER.name();
        if (tags.stream().anyMatch(t -> t.contains("food") || t.contains("еда"))) return StoryCategory.FOOD.name();
        if (tags.stream().anyMatch(t -> t.contains("nature") || t.contains("природа"))) return StoryCategory.NATURE.name();
        if (tags.stream().anyMatch(t -> t.contains("travel") || t.contains("путешествие"))) return StoryCategory.TRAVEL.name();
        return StoryCategory.OTHER.name();
    }

    private String mapColorsToMood(List<String> colors) {
        if (colors == null || colors.isEmpty()) return StoryMood.NEUTRAL.name();
        boolean hasBright = colors.stream().anyMatch(this::isBrightColor);
        boolean hasDark   = colors.stream().anyMatch(this::isDarkColor);
        if (hasBright && !hasDark) return StoryMood.HAPPY.name();
        if (!hasBright && hasDark) return StoryMood.SAD.name();
        return StoryMood.NEUTRAL.name();
    }

    private boolean isBrightColor(String hex) {
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return (r + g + b) / 3 > 127;
        } catch (Exception e) { return false; }
    }

    private boolean isDarkColor(String hex) { return !isBrightColor(hex); }
}