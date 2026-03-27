package com.example.project.rest;

import com.example.project.dto.story.StoryDTO;
import com.example.project.dto.count.StoryReactionCountDTO;
import com.example.project.dto.reaction.StoryReactionRequestDTO;
import com.example.project.entity.Story;
import com.example.project.mappers.StoryMapper;
import com.example.project.metrics.StoryApiMetricsService;
import com.example.project.service.MinioService;
import com.example.project.service.StoryServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryRestController {

    private final StoryServiceImpl storyService;
    private final StoryMapper storyMapper;
    private final MinioService minioService;
    private final StoryApiMetricsService apiMetrics;

    @GetMapping
    public ResponseEntity<List<StoryDTO>> getAllStories() {
        apiMetrics.getRequest();
        UUID userId = getCurrentUserId();
        List<StoryDTO> storyDTOs = storyService.getAllStories().stream()
                .filter(s -> !s.getUserId().equals(userId))
                .map(storyMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(storyDTOs);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryDTO> createStory(
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "overlayText", required = false) String overlayText,
            @RequestParam(value = "musicCaption", required = false) String musicCaption,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestPart("photo") MultipartFile photo) throws Exception {
        log.info("REST: создание сторис");
        apiMetrics.createRequest();

        UUID userId = getCurrentUserId();
        byte[] photoBytes;
        try { photoBytes = photo.getBytes(); }
        catch (IOException e) { throw new RuntimeException("Не удалось прочитать файл", e); }

        String photoUrl = minioService.uploadStoryPhotoBytes(photoBytes, photo.getOriginalFilename(), photo.getContentType());

        StoryDTO storyDTO = new StoryDTO();
        storyDTO.setDescription(description);
        storyDTO.setPhotoUrl(photoUrl);
        storyDTO.setUserId(userId.toString());
        storyDTO.setLocation(location);
        storyDTO.setOverlayText(overlayText);
        storyDTO.setMusicCaption(musicCaption);
        storyDTO.setPublic(isPublic);
        storyDTO.setPhotoBytes(photoBytes);
        storyDTO.setPhotoFileName(photo.getOriginalFilename());

        Story createdStory = storyService.createStory(storyDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(storyMapper.toDto(createdStory));
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Void> deleteStory(@PathVariable UUID storyId) {
        apiMetrics.deleteRequest();
        storyService.deleteStory(storyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{storyId}/view")
    public ResponseEntity<Void> viewStory(@PathVariable UUID storyId) {
        apiMetrics.viewRequest();
        storyService.viewStory(storyId, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{storyId}/views/me")
    public ResponseEntity<Boolean> hasViewed(@PathVariable UUID storyId) {
        return ResponseEntity.ok(storyService.hasViewed(storyId, getCurrentUserId()));
    }

    @GetMapping("/{storyId}/views/count")
    public ResponseEntity<Long> countViews(@PathVariable UUID storyId) {
        return ResponseEntity.ok(storyService.countViews(storyId));
    }

    @PostMapping("/{storyId}/reaction")
    public ResponseEntity<Void> reactToStory(@PathVariable UUID storyId, @RequestBody @Valid StoryReactionRequestDTO dto) {
        apiMetrics.reactionRequest();
        storyService.reactToStory(storyId, dto.getReaction());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{storyId}/reaction")
    public ResponseEntity<Void> deleteReaction(@PathVariable UUID storyId) {
        storyService.deleteReaction(storyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{storyId}/reactions")
    public ResponseEntity<List<StoryReactionCountDTO>> getReactionStats(@PathVariable UUID storyId) {
        return ResponseEntity.ok(storyService.getReactionStats(storyId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<StoryDTO>> getStoriesByUserId(@PathVariable UUID userId) {
        apiMetrics.getRequest();
        return ResponseEntity.ok(storyService.getStoriesByUserId(userId).stream().map(storyMapper::toDto).collect(Collectors.toList()));
    }

    @PutMapping("/privacy/all")
    public ResponseEntity<Void> setAllStoriesPrivacy(@RequestParam boolean isPublic) {
        storyService.setAllStoriesPrivacy(getCurrentUserId(), isPublic);
        return ResponseEntity.ok().build();
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Пользователь не авторизован");
        }
        return UUID.fromString(authentication.getName());
    }
}