package com.example.project.rest;

import com.example.project.dto.StoryDTO;
import com.example.project.dto.StoryReactionCountDTO;
import com.example.project.dto.StoryReactionRequestDTO;
import com.example.project.entity.Story;
import com.example.project.interfaces.StoryCrudService;
import com.example.project.service.StoryServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryRestController {
    private final StoryServiceImpl storyService;


    @PostMapping
    public ResponseEntity<Story> createStory(@RequestBody StoryDTO storyDTO) {
        log.info("REST: создание сторис");
        Story story = storyService.createStory(storyDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(story);
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> deleteStory(@PathVariable UUID storyId) {
        log.info("REST: удаление сторис {}", storyId);
        storyService.deleteStory(storyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{storyId}/view")
    public ResponseEntity<Void> viewStory(@PathVariable UUID storyId) {
        UUID viewerId = getCurrentUserId();
        log.info("REST: пользователь {} смотрит сторис {}", viewerId, storyId);
        storyService.viewStory(storyId, viewerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{storyId}/views/me")
    public ResponseEntity<Boolean> hasViewed(@PathVariable UUID storyId) {
        UUID viewerId = getCurrentUserId();
        log.info("REST: проверка, смотрел ли пользователь {} сторис {}", viewerId, storyId);
        boolean viewed = storyService.hasViewed(storyId, viewerId);
        log.info("REST: результат проверки просмотра: {}", viewed);

        return ResponseEntity.ok(viewed);

    }

    @GetMapping("/{storyId}/views/count")
    public ResponseEntity<Long> countViews(@PathVariable UUID storyId) {
        long count = storyService.countViews(storyId);
        log.info("REST: количество просмотров сторис {}: {}", storyId, count);
        return ResponseEntity.ok(count);
    }

    @PostMapping("/{storyId}/reaction")
    public ResponseEntity<Void> reactToStory(
            @PathVariable UUID storyId,
            @RequestBody @Valid StoryReactionRequestDTO dto
    ) {
        log.info("REST: реакция {} на историю {}", dto.getReaction(), storyId);
        storyService.reactToStory(storyId, dto.getReaction());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{storyId}/reaction")
    public ResponseEntity<Void> deleteReaction(
            @PathVariable UUID storyId
    ) {
        log.info("REST: удаление реакции с истории {}", storyId);
        storyService.deleteReaction(storyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{storyId}/reactions")
    public ResponseEntity<List<StoryReactionCountDTO>> getReactionStats(@PathVariable UUID storyId) {
        log.info("REST: получение статистики реакций для истории {}", storyId);
        return ResponseEntity.ok(storyService.getReactionStats(storyId));
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Пользователь не авторизован");
        }

        return UUID.fromString(authentication.getName());
    }
}
