package com.example.project.rest;

import com.example.project.dto.count.StoryReactionCountDTO;
import com.example.project.dto.reaction.StoryReactionRequestDTO;
import com.example.project.dto.story.StoryDTO;
import com.example.project.entity.Story;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.handlers.GlobalExceptionHandler;
import com.example.project.mappers.StoryMapper;
import com.example.project.metrics.StoryApiMetricsService;
import com.example.project.service.MinioService;
import com.example.project.service.StoryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StoryRestControllerTest {

    @Mock private StoryServiceImpl storyService;
    @Mock private StoryMapper storyMapper;
    @Mock private MinioService minioService;

    // ✅ @Mock — не @InjectMocks, нужен MeterRegistry для конструктора
    @Mock private StoryApiMetricsService apiMetrics;

    @InjectMocks
    private StoryRestController storyRestController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(storyRestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        userId = UUID.randomUUID();
        storyId = UUID.randomUUID();
    }

    // ============================================
    //  CREATE STORY (multipart)
    // ============================================

    @Test
    void createStory_success() throws Exception {
        mockAuthentication();

        Story story = validStory();
        StoryDTO dto = StoryDTO.builder()
                .description("Test story")
                .photoUrl("http://minio/stories/photo.jpg")
                .build();

        when(minioService.uploadStoryPhotoBytes(any(), anyString(), anyString()))
                .thenReturn("http://minio/stories/photo.jpg");
        when(storyService.createStory(any(StoryDTO.class), eq(userId))).thenReturn(story);
        when(storyMapper.toDto(story)).thenReturn(dto);

        mockMvc.perform(multipart("/api/stories")
                        .file(new MockMultipartFile("photo","photo.jpg","image/jpeg","img".getBytes()))
                        .param("description", "Test story")
                        .param("isPublic", "true"))
                .andExpect(status().isCreated());

        verify(storyService).createStory(any(StoryDTO.class),  eq(userId));
    }

    @Test
    void createStory_unauthorized() throws Exception {
        mockAuthentication();
        when(minioService.uploadStoryPhotoBytes(any(), any(), any()))
                .thenReturn("http://minio/x.jpg");
        when(storyService.createStory(any(), eq(userId))).thenThrow(new UnauthorizedException("Unauthorized"));

        mockMvc.perform(multipart("/api/stories")
                        .file(new MockMultipartFile("photo","photo.jpg","image/jpeg","img".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    // ============================================
    //  DELETE STORY
    // ============================================

    @Test
    void deleteStory_success() throws Exception {
        doNothing().when(storyService).deleteStory(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}", storyId))
                .andExpect(status().isNoContent());

        verify(storyService).deleteStory(storyId);
    }

    @Test
    void deleteStory_notFound() throws Exception {
        doThrow(new StoryNotFoundException("Story not found"))
                .when(storyService).deleteStory(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}", storyId))
                .andExpect(status().isNotFound());
    }

    // ============================================
    //  VIEW STORY
    // ============================================

    @Test
    void viewStory_success() throws Exception {
        mockAuthentication();
        doNothing().when(storyService).viewStory(eq(storyId), any(UUID.class));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId))
                .andExpect(status().isOk());

        verify(storyService).viewStory(eq(storyId), any(UUID.class));
    }

    @Test
    void viewStory_notFound() throws Exception {
        mockAuthentication();
        doThrow(new StoryNotFoundException("Story not found"))
                .when(storyService).viewStory(eq(storyId), any(UUID.class));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewStory_expired() throws Exception {
        mockAuthentication();
        doThrow(new StoryIsNotAviableByTimeException("Story expired"))
                .when(storyService).viewStory(eq(storyId), any(UUID.class));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId))
                .andExpect(status().isGone());
    }

    @Test
    void viewStory_correctViewerId() throws Exception {
        mockAuthentication();
        doNothing().when(storyService).viewStory(eq(storyId), eq(userId));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId));

        verify(storyService).viewStory(eq(storyId), eq(userId));
    }

    // ============================================
    //  HAS VIEWED
    // ============================================

    @Test
    void hasViewed_true() throws Exception {
        mockAuthentication();
        when(storyService.hasViewed(eq(storyId), any(UUID.class))).thenReturn(true);

        mockMvc.perform(get("/api/stories/{storyId}/views/me", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void hasViewed_false() throws Exception {
        mockAuthentication();
        when(storyService.hasViewed(eq(storyId), any(UUID.class))).thenReturn(false);

        mockMvc.perform(get("/api/stories/{storyId}/views/me", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void hasViewed_correctViewerId() throws Exception {
        mockAuthentication();
        when(storyService.hasViewed(eq(storyId), eq(userId))).thenReturn(true);

        mockMvc.perform(get("/api/stories/{storyId}/views/me", storyId));

        verify(storyService).hasViewed(eq(storyId), eq(userId));
    }

    // ============================================
    //  COUNT VIEWS
    // ============================================

    @Test
    void countViews_success() throws Exception {
        when(storyService.countViews(storyId)).thenReturn(10L);

        mockMvc.perform(get("/api/stories/{storyId}/views/count", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @Test
    void countViews_zero() throws Exception {
        when(storyService.countViews(storyId)).thenReturn(0L);

        mockMvc.perform(get("/api/stories/{storyId}/views/count", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    // ============================================
    //  REACT TO STORY
    // ============================================

    @Test
    void reactToStory_success() throws Exception {
        StoryReactionRequestDTO dto = new StoryReactionRequestDTO();
        dto.setReaction(Reactions.LIKE);
        doNothing().when(storyService).reactToStory(storyId, Reactions.LIKE);

        mockMvc.perform(post("/api/stories/{storyId}/reaction", storyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(storyService).reactToStory(storyId, Reactions.LIKE);
    }

    @Test
    void reactToStory_storyNotFound() throws Exception {
        StoryReactionRequestDTO dto = new StoryReactionRequestDTO();
        dto.setReaction(Reactions.LIKE);
        doThrow(new StoryNotFoundException("Story not found"))
                .when(storyService).reactToStory(storyId, Reactions.LIKE);

        mockMvc.perform(post("/api/stories/{storyId}/reaction", storyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reactToStory_storyExpired() throws Exception {
        StoryReactionRequestDTO dto = new StoryReactionRequestDTO();
        dto.setReaction(Reactions.LIKE);
        doThrow(new StoryIsNotAviableByTimeException("Expired"))
                .when(storyService).reactToStory(storyId, Reactions.LIKE);

        mockMvc.perform(post("/api/stories/{storyId}/reaction", storyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isGone());
    }

    @Test
    void reactToStory_serviceMethodCalled() throws Exception {
        StoryReactionRequestDTO dto = new StoryReactionRequestDTO();
        dto.setReaction(Reactions.FIRE);

        mockMvc.perform(post("/api/stories/{storyId}/reaction", storyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)));

        verify(storyService).reactToStory(storyId, Reactions.FIRE);
    }

    // ============================================
    //  DELETE REACTION
    // ============================================

    @Test
    void deleteReaction_success() throws Exception {
        doNothing().when(storyService).deleteReaction(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}/reaction", storyId))
                .andExpect(status().isNoContent());

        verify(storyService).deleteReaction(storyId);
    }

    @Test
    void deleteReaction_storyNotFound() throws Exception {
        doThrow(new StoryNotFoundException("Not found"))
                .when(storyService).deleteReaction(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}/reaction", storyId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReaction_storyExpired() throws Exception {
        doThrow(new StoryIsNotAviableByTimeException("Expired"))
                .when(storyService).deleteReaction(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}/reaction", storyId))
                .andExpect(status().isGone());
    }

    @Test
    void deleteReaction_serviceMethodCalled() throws Exception {
        mockMvc.perform(delete("/api/stories/{storyId}/reaction", storyId));
        verify(storyService).deleteReaction(storyId);
    }

    // ============================================
    //  REACTION STATS
    // ============================================

    @Test
    void getReactionStats_success() throws Exception {
        when(storyService.getReactionStats(storyId)).thenReturn(List.of(
                new StoryReactionCountDTO(Reactions.LIKE, 3L),
                new StoryReactionCountDTO(Reactions.FIRE, 1L)
        ));

        mockMvc.perform(get("/api/stories/{storyId}/reactions", storyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reaction").value("LIKE"))
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    void getReactionStats_empty() throws Exception {
        when(storyService.getReactionStats(storyId)).thenReturn(List.of());

        mockMvc.perform(get("/api/stories/{storyId}/reactions", storyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============================================
    //  GET STORIES BY USER ID
    // ============================================

    @Test
    void getStoriesByUserId_success() throws Exception {
        Story story = Story.builder().id(storyId).userId(userId).description("Story text").build();
        StoryDTO dto = StoryDTO.builder().description("Story text").build();

        when(storyService.getStoriesByUserId(userId)).thenReturn(List.of(story));
        when(storyMapper.toDto(story)).thenReturn(dto);

        mockMvc.perform(get("/api/stories/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Story text"));
    }

    @Test
    void getStoriesByUserId_emptyList() throws Exception {
        when(storyService.getStoriesByUserId(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/stories/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getStoriesByUserId_multipleStories() throws Exception {
        Story s1 = Story.builder().id(UUID.randomUUID()).description("One").build();
        Story s2 = Story.builder().id(UUID.randomUUID()).description("Two").build();
        when(storyService.getStoriesByUserId(userId)).thenReturn(List.of(s1, s2));
        when(storyMapper.toDto(s1)).thenReturn(StoryDTO.builder().description("One").build());
        when(storyMapper.toDto(s2)).thenReturn(StoryDTO.builder().description("Two").build());

        mockMvc.perform(get("/api/stories/user/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getStoriesByUserId_serviceThrows() throws Exception {
        when(storyService.getStoriesByUserId(userId)).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/stories/user/{userId}", userId))
                .andExpect(status().isInternalServerError());
    }

    // ============================================
    //  HELPERS
    // ============================================

    private void mockAuthentication() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    private Story validStory() {
        return Story.builder()
                .id(storyId).userId(userId)
                .description("Test story")
                .photoUrl("http://example.com/photo.jpg")
                .createdAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusHours(24))
                .viewers(new ArrayList<>())
                .build();
    }
}