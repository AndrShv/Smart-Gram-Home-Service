package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.dto.StoryDTO;
import com.example.project.dto.StoryReactionCountDTO;
import com.example.project.dto.UserResponseDTO;
import com.example.project.entity.Story;
import com.example.project.entity.StoryReaction;
import com.example.project.entity.StoryViewer;
import com.example.project.enums.PostCategory;
import com.example.project.enums.Reactions;
import com.example.project.enums.StoryCategory;
import com.example.project.enums.StoryMood;
import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.ImaggaService;
import com.example.project.interfaces.StoryCrudService;
import com.example.project.interfaces.StoryReactionService;
import com.example.project.repository.StoryReactionRepository;
import com.example.project.repository.StoryRepository;
import com.example.project.repository.StoryViewerRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Builder
@AllArgsConstructor
public class StoryServiceImpl implements StoryCrudService, StoryReactionService {


    private static final long STORY_LIFETIME_HOURS = 24;

    private final AuthClient authClient;
    private final StoryRepository storyRepository;
    private final StoryViewerRepository storyViewerRepository;
    private final StoryReactionRepository storyReactionRepository;
    private final ImaggaService imaggaService;


    @Override
    @Transactional
    public Story createStory(StoryDTO storyDTO) {
        log.info("Создание новой истории");

        UserResponseDTO currentUser = authClient.getCurrentUser();
        UUID userId = UUID.fromString(currentUser.getId());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("JWT истёк");
        }

        List<String> tags = new ArrayList<>();
        String category = String.valueOf(StoryCategory.OTHER);;
        String mood = String.valueOf(StoryMood.NEUTRAL);

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

        storyRepository.save(story);
        log.info("✅ История успешно создана для пользователя с ID: {}", userId);
        return story;
    }

    @Override
    public void deleteStory(UUID storyId) {
        log.info("Удаление истории с ID: {}", storyId);
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> {
                    log.warn("❌ История с ID: {} не найдена", storyId);
                    return new StoryNotFoundException("История с ID " + storyId + " не найдена");
                });

    }

    @Override
    public void viewStory(UUID storyId, UUID viewerId) {
        log.info("Пользователь с ID: {} просматривает историю с ID: {}", viewerId, storyId);
        UUID storyUUID = UUID.fromString(String.valueOf(storyId));
        Story story = storyRepository.findById(storyUUID)
                .orElseThrow(() -> {
                    log.warn("❌ История с ID: {} не найдена", storyId);
                    return new StoryNotFoundException("История с ID " + storyId + " не найдена");
                });


        if (story.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("❌ История с ID: {} истекла и не может быть просмотрена", storyId);
            throw new StoryIsNotAviableByTimeException("История с ID " + storyId + " истекла и не может быть просмотрена");
        }

        if (story.getViewers().contains(viewerId)) {
            log.info("Пользователь с ID: {} уже просматривал историю с ID: {}", viewerId, storyId);
            return;
        }

        log.info("Проверка, просматривал ли пользователь с ID: {} историю с ID: {}", viewerId, storyId);
        boolean alreadyViewed = storyViewerRepository.existsByStoryIdAndViewerId(storyId, viewerId);

        if (alreadyViewed) {
            return;
        }

        log.info("Добавление просмотра истории с ID: {} пользователем с ID: {}", storyId, viewerId);
        StoryViewer viewer = new StoryViewer();
        viewer.setStory(story);
        viewer.setViewerId(viewerId);
        viewer.setViewedAt(LocalDateTime.now());
        log.info("Сохранение просмотра истории с ID: {} пользователем с ID: {} в репозиторий", storyId, viewerId);

        storyViewerRepository.save(viewer);

    }

    @Override
    public boolean hasViewed(UUID storyId, UUID viewerId) {
        log.info("Проверка, просматривал ли пользователь с ID: {} историю с ID: {}", viewerId, storyId);
        return storyViewerRepository.existsByStoryIdAndViewerId(storyId, viewerId);
    }

    @Override
    public long countViews(UUID storyId) {
        log.info("Подсчет количества просмотров истории с ID: {}", storyId);
        return storyViewerRepository.countByStoryId(storyId);
    }

    @Transactional
    public void reactToStory(UUID storyId, Reactions reaction) {

        log.info("Попытка достать текущего пользователя из AuthClient для реакции на историю с ID: {}", storyId);
        UserResponseDTO currentUser = authClient.getCurrentUser();

        UUID userId = UUID.fromString(currentUser.getId());
        log.info("Получин пользователь с ID: {} для реакции на историю с ID: {}", userId, storyId);


        log.info("Попытка достать историю с ID: {} для реакции", storyId);
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException("Story not found"));


        log.info("Проверка, истекла ли история с ID: {} для реакции", storyId);
        Optional<StoryReaction> existing =
                storyReactionRepository.findByStoryIdAndUserId(storyId, userId);


        log.info("Проверка наличия существующей реакции пользователя с ID: {} на историю с ID: {}", userId, storyId);
        if (existing.isPresent()) {
            existing.get().setReaction(reaction);
            existing.get().setReactedAt(LocalDateTime.now());
            return;
        }

        log.info("Добавление новой реакции пользователя с ID: {} на историю с ID: {}", userId, storyId);
        StoryReaction storyReaction = StoryReaction.builder()
                .story(story)
                .userId(userId)
                .reaction(reaction)
                .reactedAt(LocalDateTime.now())
                .build();


        storyReactionRepository.save(storyReaction);
        log.info("✅ Реакция пользователя с ID: {} на историю с ID: {} успешно сохранена", userId, storyId);
    }

    @Override
    public void deleteReaction(UUID storyId) {
        log.info("Попытка достать текущего пользователя из AuthClient для удаления реакции на историю с ID: {}", storyId);
        UserResponseDTO currentUser = authClient.getCurrentUser();
        UUID userId = UUID.fromString(currentUser.getId());

        log.info("Получин пользователь с ID: {} для удаления реакции на историю с ID: {}", userId, storyId);

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> {
                    log.warn("❌ История с ID: {} не найдена для удаления реакции", storyId);
                    return new StoryNotFoundException("История с ID " + storyId + " не найдена");
                });

        Optional<StoryReaction> existing = Optional
                .ofNullable(storyReactionRepository.findByStoryIdAndUserId(storyId, userId)
                        .orElseThrow(() -> {
                            log.warn("❌ Реакция пользователя с ID: {} на историю с ID: {} не найдена для удаления", userId, storyId);
                            return new StoryNotFoundException("Реакция пользователя с ID " + userId + " на историю с ID " + storyId + " не найдена");
                        }));

        if (story.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("❌ История с ID: {} истекла и реакция не может быть удалена", storyId);
            throw new StoryIsNotAviableByTimeException("История с ID " + storyId + " истекла и реакция не может быть удалена");
        }
        if (existing.isPresent()) {
            log.info("Удаление реакции пользователя с ID: {} на историю с ID: {}", userId, storyId);
            storyReactionRepository.delete(existing.get());
            log.info("✅ Реакция пользователя с ID: {} на историю с ID: {} успешно удалена", userId, storyId);
        }

    }

    @Override
    public List<StoryReactionCountDTO> getReactionStats(UUID storyId) {
        log.info("Получение статистики реакций для истории с ID: {}", storyId);
        return storyReactionRepository.countReactionsByStory(storyId)
                .stream()
                .map(r -> new StoryReactionCountDTO(
                        (Reactions) r[0],
                        (Long) r[1]
                ))
                .toList();
    }

    @Override
    public List<Story> getAllActiveStories() {
        log.info("Получение всех активных историй");
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByExpireAtAfter(now);
    }

    @Override
    public List<Story> getStoriesByUserId(UUID userId) {
        log.info("Получение историй пользователя с ID: {}", userId);
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByUserIdAndExpireAtAfterOrderByCreatedAtDesc(userId, now);
    }

    @Override
    public Story getStoryById(UUID storyId) {
        log.info("Получение истории с ID: {}", storyId);
        return storyRepository.findById(storyId)
                .orElseThrow(() -> {
                    log.warn("❌ История с ID: {} не найдена", storyId);
                    return new StoryNotFoundException("История с ID " + storyId + " не найдена");
                });
    }

    private String mapTagsToCategory(List<String> tags) {
        if (tags == null) return StoryCategory.OTHER.name();
        if (tags.stream().anyMatch(t -> t.contains("food") || t.contains("еда"))) return StoryCategory.FOOD.name();
        if (tags.stream().anyMatch(t -> t.contains("nature") || t.contains("природа"))) return StoryCategory.NATURE.name();
        if (tags.stream().anyMatch(t -> t.contains("travel") || t.contains("путешествие"))) return StoryCategory.TRAVEL.name();
        return StoryCategory.OTHER.name();
    }

    private String mapColorsToMood(List<String> colors) {
        if (colors == null || colors.isEmpty()) return StoryMood.NEUTRAL.name();

        boolean hasBright = colors.stream().anyMatch(c -> isBrightColor(c));
        boolean hasDark = colors.stream().anyMatch(c -> isDarkColor(c));

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





}


