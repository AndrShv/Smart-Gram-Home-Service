package com.example.project.rest;

import com.example.project.dto.StoryDTO;
import com.example.project.dto.StoryReactionCountDTO;
import com.example.project.dto.StoryReactionRequestDTO;
import com.example.project.entity.Story;
import com.example.project.interfaces.StoryCrudService;
import com.example.project.mappers.StoryMapper;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoryDTO> createStory(
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "overlayText", required = false) String overlayText,
            @RequestParam(value = "musicCaption", required = false) String musicCaption,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestPart("photo") MultipartFile photo
    ) {
        log.info("REST: создание сторис");

        UUID userId = getCurrentUserId();

        byte[] photoBytes;
        try {
            photoBytes = photo.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать файл", e);
        }

        String photoUrl = minioService.uploadStoryPhotoBytes(
                photoBytes,
                photo.getOriginalFilename(),
                photo.getContentType()
        );

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
        StoryDTO responseDTO = storyMapper.toDto(createdStory);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
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

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<StoryDTO>> getStoriesByUserId(@PathVariable UUID userId) {
        log.info("REST: получение историй пользователя {}", userId);
        List<Story> stories = storyService.getStoriesByUserId(userId);
        List<StoryDTO> storyDTOs = stories.stream()
                .map(storyMapper::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(storyDTOs);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Пользователь не авторизован");
        }

        return UUID.fromString(authentication.getName());
    }



}
