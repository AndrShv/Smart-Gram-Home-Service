package com.example.project.rest;

import com.example.project.dto.StoryDTO;
import com.example.project.dto.StoryReactionCountDTO;
import com.example.project.dto.StoryReactionRequestDTO;
import com.example.project.entity.Story;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.handlers.GlobalExceptionHandler;
import com.example.project.interfaces.StoryCrudService;
import com.example.project.service.StoryServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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

    @Mock
    private StoryServiceImpl storyService;

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
        userId = UUID.randomUUID();
        storyId = UUID.randomUUID();
        objectMapper.findAndRegisterModules();
    }

    @Test
    void createStory_success() throws Exception {
        StoryDTO storyDTO = StoryDTO.builder()
                .description("Test story")
                .photoUrl("http://example.com/photo.jpg")
                .build();

        Story story = Story.builder()
                .id(storyId)
                .userId(userId)
                .description("Test story")
                .photoUrl("http://example.com/photo.jpg")
                .createdAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusHours(24))
                .viewers(new ArrayList<>())
                .build();

        when(storyService.createStory(any(StoryDTO.class))).thenReturn(story);

        mockMvc.perform(post("/api/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(storyDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(storyId.toString()))
                .andExpect(jsonPath("$.description").value("Test story"))
                .andExpect(jsonPath("$.photoUrl").value("http://example.com/photo.jpg"));

        verify(storyService, times(1)).createStory(any(StoryDTO.class));
    }

    @Test
    void createStory_serviceMethodCalled() throws Exception {
        StoryDTO storyDTO = new StoryDTO();
        Story story = Story.builder()
                .id(storyId)
                .userId(userId)
                .viewers(new ArrayList<>())
                .build();

        when(storyService.createStory(any(StoryDTO.class))).thenReturn(story);

        mockMvc.perform(post("/api/stories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(storyDTO)));

        verify(storyService).createStory(any(StoryDTO.class));
    }

    @Test
    void createStory_returnsCreatedStatus() throws Exception {
        StoryDTO storyDTO = new StoryDTO();
        Story story = Story.builder()
                .id(storyId)
                .viewers(new ArrayList<>())
                .build();

        when(storyService.createStory(any(StoryDTO.class))).thenReturn(story);

        mockMvc.perform(post("/api/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(storyDTO)))
                .andExpect(status().isCreated());
    }

    @Test
    void createStory_unauthorized() throws Exception {
        StoryDTO storyDTO = new StoryDTO();

        when(storyService.createStory(any(StoryDTO.class)))
                .thenThrow(new UnauthorizedException("Unauthorized"));

        mockMvc.perform(post("/api/stories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(storyDTO)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteStory_success() throws Exception {
        doNothing().when(storyService).deleteStory(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}", storyId))
                .andExpect(status().isNoContent());

        verify(storyService, times(1)).deleteStory(storyId);
    }

    @Test
    void deleteStory_notFound() throws Exception {
        doThrow(new StoryNotFoundException("Story not found"))
                .when(storyService).deleteStory(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}", storyId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteStory_serviceMethodCalled() throws Exception {
        doNothing().when(storyService).deleteStory(storyId);

        mockMvc.perform(delete("/api/stories/{storyId}", storyId));

        verify(storyService).deleteStory(storyId);
    }

    @Test
    void viewStory_success() throws Exception {
        mockAuthentication();
        doNothing().when(storyService).viewStory(eq(storyId), any(UUID.class));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId))
                .andExpect(status().isOk());

        verify(storyService, times(1)).viewStory(eq(storyId), any(UUID.class));
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
    void viewStory_serviceMethodCalled() throws Exception {
        mockAuthentication();
        doNothing().when(storyService).viewStory(eq(storyId), any(UUID.class));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId));

        verify(storyService).viewStory(eq(storyId), any(UUID.class));
    }

    @Test
    void viewStory_correctViewerId() throws Exception {
        mockAuthentication();
        doNothing().when(storyService).viewStory(eq(storyId), eq(userId));

        mockMvc.perform(post("/api/stories/{storyId}/view", storyId));

        verify(storyService).viewStory(eq(storyId), eq(userId));
    }

    @Test
    void hasViewed_true() throws Exception {
        mockAuthentication();
        when(storyService.hasViewed(eq(storyId), any(UUID.class))).thenReturn(true);

        mockMvc.perform(get("/api/stories/{storyId}/views/me", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(storyService, times(1)).hasViewed(eq(storyId), any(UUID.class));
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
    void hasViewed_serviceMethodCalled() throws Exception {
        mockAuthentication();
        when(storyService.hasViewed(eq(storyId), any(UUID.class))).thenReturn(false);

        mockMvc.perform(get("/api/stories/{storyId}/views/me", storyId));

        verify(storyService).hasViewed(eq(storyId), any(UUID.class));
    }

    @Test
    void hasViewed_correctViewerId() throws Exception {
        mockAuthentication();
        when(storyService.hasViewed(eq(storyId), eq(userId))).thenReturn(true);

        mockMvc.perform(get("/api/stories/{storyId}/views/me", storyId));

        verify(storyService).hasViewed(eq(storyId), eq(userId));
    }

    @Test
    void countViews_success() throws Exception {
        when(storyService.countViews(storyId)).thenReturn(10L);

        mockMvc.perform(get("/api/stories/{storyId}/views/count", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));

        verify(storyService, times(1)).countViews(storyId);
    }

    @Test
    void countViews_zero() throws Exception {
        when(storyService.countViews(storyId)).thenReturn(0L);

        mockMvc.perform(get("/api/stories/{storyId}/views/count", storyId))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void countViews_serviceMethodCalled() throws Exception {
        when(storyService.countViews(storyId)).thenReturn(5L);

        mockMvc.perform(get("/api/stories/{storyId}/views/count", storyId));

        verify(storyService).countViews(storyId);
    }

    @Test
    void countViews_correctStoryId() throws Exception {
        when(storyService.countViews(storyId)).thenReturn(7L);

        mockMvc.perform(get("/api/stories/{storyId}/views/count", storyId));

        verify(storyService).countViews(eq(storyId));
    }

    private void mockAuthentication() {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(userId.toString());
        when(securityContext.getAuthentication()).thenReturn(authentication);

        SecurityContextHolder.setContext(securityContext);
    }


    @Test
    void reactToStory_success() throws Exception {

        var dto = new StoryReactionRequestDTO();
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
        var dto = new StoryReactionRequestDTO();
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

        var dto = new StoryReactionRequestDTO();
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

        var dto = new StoryReactionRequestDTO();
        dto.setReaction(Reactions.FIRE);

        mockMvc.perform(post("/api/stories/{storyId}/reaction", storyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)));

        verify(storyService).reactToStory(storyId, Reactions.FIRE);
    }


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


    @Test
    void getReactionStats_success() throws Exception {
        var stats = List.of(
                new StoryReactionCountDTO(Reactions.LIKE, 3L),
                new StoryReactionCountDTO(Reactions.FIRE, 1L)
        );

        when(storyService.getReactionStats(storyId)).thenReturn(stats);

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


    @Test
    void getReactionStats_serviceMethodCalled() throws Exception {
        when(storyService.getReactionStats(storyId)).thenReturn(List.of());

        mockMvc.perform(get("/api/stories/{storyId}/reactions", storyId));

        verify(storyService).getReactionStats(storyId);
    }


}