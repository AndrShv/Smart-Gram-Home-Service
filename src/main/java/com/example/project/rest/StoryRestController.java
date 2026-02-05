package com.example.project.rest;

import com.example.project.dto.StoryDTO;
import com.example.project.entity.Story;
import com.example.project.interfaces.StoryCrudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryRestController {
    private final StoryCrudService storyService;


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
        boolean viewed = storyService.hasViewed(storyId, viewerId);
        return ResponseEntity.ok(viewed);
    }

    @GetMapping("/{storyId}/views/count")
    public ResponseEntity<Long> countViews(@PathVariable UUID storyId) {
        long count = storyService.countViews(storyId);
        return ResponseEntity.ok(count);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Пользователь не авторизован");
        }

        return UUID.fromString(authentication.getName());
    }
}
