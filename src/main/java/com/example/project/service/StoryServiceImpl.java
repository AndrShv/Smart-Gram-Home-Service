package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.dto.StoryDTO;
import com.example.project.dto.UserResponseDTO;
import com.example.project.entity.Story;
import com.example.project.entity.StoryViewer;
import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.StoryCrudService;
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
import java.util.ArrayList;
import java.util.UUID;

@Slf4j
@Service
@Builder
@AllArgsConstructor
public class StoryServiceImpl implements StoryCrudService {


    private static final long STORY_LIFETIME_HOURS = 24;

    private final AuthClient authClient;
    private final StoryRepository storyRepository;
    private final StoryViewerRepository storyViewerRepository;

    @Override
    @Transactional
    public Story createStory(StoryDTO storyDTO) {
        log.info("Создание новой истории");

        log.info("Получение текущего пользователя из AuthClient");
        UserResponseDTO currentUser = authClient.getCurrentUser();
        UUID userId = UUID.fromString(currentUser.getId());


        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("JWT истёк");
        }

        log.info("Получин пользователь с ID: {}", userId);

        Story story = Story.builder()
                .userId(userId)
                .description(storyDTO.getDescription())
                .photoUrl(storyDTO.getPhotoUrl())
                .createdAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusHours(STORY_LIFETIME_HOURS))
                .viewers(new ArrayList<>()).build();

        log.info("Попытка сохарнения истории в репозиторий с ID пользователя: {}", userId);
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
}
