package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.AiImageResult;
import com.example.project.dto.story.StoryDTO;
import com.example.project.dto.count.StoryReactionCountDTO;
import com.example.project.dto.profile.SubscriberDTO;
import com.example.project.dto.user.ShortUserDTO;
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
import com.example.project.interfaces.AiImageService;
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
    private final AiImageService aiImageService;
    private final NotificationProducer notificationProducer;
    private final StoryMetricsService storyMetrics;
    private final HomeRabbitMetricsService rabbitMetrics;
    private final StoryAsyncService storyAsyncService;

    @Override
    @Transactional
    public Story createStory(StoryDTO storyDTO) throws Exception {
        log.info("Создание новой истории");



        return storyMetrics.createStoryTimer().recordCallable(() -> {
            UserResponseDTO currentUser = getAuthenticatedUser();
            UUID userId = UUID.fromString(currentUser.getId());

            String username = currentUser.getUsername() != null && !currentUser.getUsername().isBlank()
                    ? currentUser.getUsername()
                    : userId.toString();

            Story story = Story.builder()
                    .userId(userId)
                    .description(storyDTO.getDescription())
                    .photoUrl(storyDTO.getPhotoUrl())
                    .createdAt(LocalDateTime.now())
                    .expireAt(LocalDateTime.now().plusHours(STORY_LIFETIME_HOURS))
                    .tags(new ArrayList<>())
                    .category(StoryCategory.OTHER)
                    .location(storyDTO.getLocation())
                    .mood(StoryMood.NEUTRAL)
                    .overlayText(storyDTO.getOverlayText())
                    .musicCaption(storyDTO.getMusicCaption())
                    .isPublic(storyDTO.isPublic())
                    .viewers(new ArrayList<>())
                    .build();

            Story savedStory = storyRepository.save(story);

            storyAsyncService.createStoryAsync(savedStory, userId);
            storyAsyncService.sendNotificationsAsync(savedStory, userId, username);

            log.info("История с ID: {} успешно создана", savedStory.getId());
            return savedStory;
        });
    }

    @Override
    @Transactional
    public void deleteStory(UUID storyId) {
        log.info("Удаление истории с ID: {}", storyId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID userId = UUID.fromString(currentUser.getId());

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> {
                    log.warn("❌ История с ID: {} не найдена", storyId);
                    return new StoryNotFoundException("История с ID " + storyId + " не найдена");
                });

        validateStoryOwnership(story, userId);

        storyRepository.deleteById(storyId);
        storyMetrics.incrementDeleted();
    }

    @Override
    @Transactional
    public void viewStory(UUID storyId, UUID viewerId) {
        log.info("Пользователь {} просматривает историю {}", viewerId, storyId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        if (!currentUserId.equals(viewerId)) {
            throw new UnauthorizedException("Нельзя просматривать историю от имени другого пользователя");
        }

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

        try {
            if (!story.getUserId().equals(viewerId)) {
                notificationProducer.sendStoryViewed(
                        storyId.toString(),
                        viewerId.toString(),
                        story.getUserId().toString(),
                        currentUser.getUsername() != null ? currentUser.getUsername() : viewerId.toString()
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

    @Override
    @Transactional
    public void reactToStory(UUID storyId, Reactions reaction) {
        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID userId = UUID.fromString(currentUser.getId());

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("Story not found"));

        if (story.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new StoryIsNotAviableByTimeException("История истекла");
        }

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
    @Transactional
    public void deleteReaction(UUID storyId) {
        UserResponseDTO currentUser = getAuthenticatedUser();
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
        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        if (!currentUserId.equals(userId)) {
            throw new UnauthorizedException("Нельзя менять приватность чужих историй");
        }

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
        boolean hasDark = colors.stream().anyMatch(this::isDarkColor);
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
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDarkColor(String hex) {
        return !isBrightColor(hex);
    }

    private UserResponseDTO getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("JWT истёк");
        }

        UserResponseDTO currentUser = authClient.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("Пользователь не найден");
        }

        return currentUser;
    }

    private void validateStoryOwnership(Story story, UUID userId) {
        if (!story.getUserId().equals(userId)) {
            throw new UnauthorizedException("Вы не можете изменять или удалять чужую историю");
        }
    }
}