package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.dto.StoryDTO;
import com.example.project.dto.UserResponseDTO;
import com.example.project.entity.Story;
import com.example.project.entity.StoryReaction;
import com.example.project.entity.StoryViewer;
import com.example.project.enums.Reactions;
import com.example.project.enums.StoryCategory;
import com.example.project.enums.StoryMood;
import com.example.project.exceptions.StoryIsNotAviableByTimeException;
import com.example.project.exceptions.StoryNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.repository.StoryReactionRepository;
import com.example.project.repository.StoryRepository;
import com.example.project.repository.StoryViewerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class StoryServiceImplTest {

    @Mock
    private AuthClient authClient;

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private StoryReactionRepository storyReactionRepository;

    @Mock
    private StoryViewerRepository storyViewerRepository;

    @InjectMocks
    private StoryServiceImpl storyService;

    private UUID userId;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storyId = UUID.randomUUID();
    }

    @Test
    void createStory_success() {
        StoryDTO dto = new StoryDTO();
        dto.setCategory(String.valueOf(StoryCategory.FAMILY));
        dto.setMood(String.valueOf(StoryMood.HAPPY));
        dto.setDescription("Test story");
        dto.setPhotoUrl("http://example.com/photo.jpg");
        dto.setPublic(true);
        dto.setOverlayText("Overlay text");
        dto.setMusicCaption("Music caption");
        dto.setTags(List.of("tag1", "tag2"));
        dto.setLocation("Odesa");

        UserResponseDTO user = UserResponseDTO.builder()
                .id(userId.toString())
                .build();

        when(authClient.getCurrentUser()).thenReturn(user);
        mockAuthenticated();

        Story result = storyService.createStory(dto);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("FAMILY", result.getCategory().name());
        assertEquals("HAPPY", result.getMood().name());
        verify(storyRepository).save(any(Story.class));
    }

    @Test
    void createStory_unauthorized() {
        StoryDTO dto = new StoryDTO();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );

        SecurityContextHolder.clearContext();

        assertThrows(UnauthorizedException.class,
                () -> storyService.createStory(dto));
    }


    @Test
    void createStory_expireTimeIs24Hours() {
        StoryDTO dto = new StoryDTO();
        dto.setCategory(String.valueOf(StoryCategory.FRIENDS));
        dto.setMood(String.valueOf(StoryMood.HAPPY));

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        mockAuthenticated();

        Story story = storyService.createStory(dto);

        assertTrue(story.getExpireAt().isAfter(story.getCreatedAt()));
    }

    @Test
    void createStory_viewersEmpty() {
        StoryDTO dto = new StoryDTO();
        dto.setCategory(String.valueOf(StoryCategory.FRIENDS));
        dto.setMood(String.valueOf(StoryMood.HAPPY));

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        mockAuthenticated();

        Story story = storyService.createStory(dto);

        assertNotNull(story.getViewers());
        assertTrue(story.getViewers().isEmpty());
    }


    @Test
    void createStory_descriptionSaved() {
        StoryDTO dto = new StoryDTO();
        dto.setDescription("hello");
        dto.setCategory(String.valueOf(StoryCategory.FRIENDS));
        dto.setMood(String.valueOf(StoryMood.HAPPY));

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        mockAuthenticated();

        Story story = storyService.createStory(dto);

        assertEquals("hello", story.getDescription());
    }


    @Test
    void createStory_photoUrlSaved() {
        StoryDTO dto = new StoryDTO();
        dto.setPhotoUrl("img.png");
        dto.setCategory(String.valueOf(StoryCategory.FRIENDS));
        dto.setMood(String.valueOf(StoryMood.HAPPY));

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        mockAuthenticated();

        Story story = storyService.createStory(dto);

        assertEquals("img.png", story.getPhotoUrl());
    }


    @Test
    void createStory_saveCalledOnce() {
        StoryDTO dto = new StoryDTO();
        dto.setCategory(String.valueOf(StoryCategory.FAMILY));
        dto.setMood(String.valueOf(StoryMood.HAPPY));
        dto.setDescription("Test story");

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        mockAuthenticated();

        storyService.createStory(dto);

        verify(storyRepository).save(any());
    }

    @Test
    void viewStory_success() {
        Story story = validStory();

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository).save(any(StoryViewer.class));
    }

    @Test
    void viewStory_storyNotFound() {
        when(storyRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThrows(StoryNotFoundException.class,
                () -> storyService.viewStory(storyId, userId));
    }


    @Test
    void viewStory_storyExpired() {
        Story story = validStory();
        story.setExpireAt(LocalDateTime.now().minusMinutes(1));

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

        assertThrows(StoryIsNotAviableByTimeException.class,
                () -> storyService.viewStory(storyId, userId));
    }



    @Test
    void viewStory_alreadyViewed() {
        Story story = validStory();

        when(storyRepository.findById(storyId))
                .thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(true);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository, never()).save(any());
    }




    @Test
    void viewStory_viewerIdCorrect() {
        Story story = validStory();

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository)
                .save(argThat(v -> v.getViewerId().equals(userId)));
    }


    @Test
    void viewStory_viewedAtSet() {
        Story story = validStory();

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository)
                .save(argThat(v -> v.getViewedAt() != null));
    }


    @Test
    void viewStory_storyLinked() {
        Story story = validStory();

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository)
                .save(argThat(v -> v.getStory().equals(story)));
    }


    @Test
    void viewStory_saveCalledOnce() {
        Story story = validStory();

        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository).save(any());
    }


    @Test
    void hasViewed_true() {
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(true);

        assertTrue(storyService.hasViewed(storyId, userId));
    }


    @Test
    void hasViewed_false() {
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId))
                .thenReturn(false);

        assertFalse(storyService.hasViewed(storyId, userId));
    }


    @Test
    void countViews_success() {
        when(storyViewerRepository.countByStoryId(storyId)).thenReturn(5L);

        long count = storyService.countViews(storyId);

        assertEquals(5L, count);
    }


    @Test
    void countViews_zero() {
        when(storyViewerRepository.countByStoryId(storyId)).thenReturn(0L);

        assertEquals(0L, storyService.countViews(storyId));
    }


    @Test
    void countViews_repositoryCalled() {
        storyService.countViews(storyId);

        verify(storyViewerRepository).countByStoryId(storyId);
    }

    private void mockAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Story validStory() {
        return Story.builder()
                .id(storyId)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusHours(1))
                .viewers(new ArrayList<>())
                .build();
    }

    @Test
    void reactToStory_newReaction_saved() {
        Story story = validStory();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.empty());

        storyService.reactToStory(storyId, Reactions.LIKE);

        verify(storyReactionRepository).save(any(StoryReaction.class));
    }

    @Test
    void reactToStory_existingReaction_updated() {
        Story story = validStory();
        StoryReaction existing = StoryReaction.builder()
                .story(story)
                .userId(userId)
                .reaction(Reactions.LOVE)
                .reactedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.of(existing));

        storyService.reactToStory(storyId, Reactions.FIRE);

        assertEquals(Reactions.FIRE, existing.getReaction());
        assertNotNull(existing.getReactedAt());
        verify(storyReactionRepository, never()).save(any());
    }


    @Test
    void reactToStory_storyNotFound() {
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThrows(StoryNotFoundException.class,
                () -> storyService.reactToStory(storyId, Reactions.LIKE));
    }


    @Test
    void reactToStory_reactionFieldsCorrect() {
        Story story = validStory();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.empty());

        storyService.reactToStory(storyId, Reactions.LOVE);

        verify(storyReactionRepository).save(argThat(r ->
                r.getReaction() == Reactions.LOVE &&
                        r.getUserId().equals(userId) &&
                        r.getStory().equals(story) &&
                        r.getReactedAt() != null
        ));
    }


    @Test
    void deleteReaction_success() {
        Story story = validStory();
        StoryReaction reaction = StoryReaction.builder()
                .story(story)
                .userId(userId)
                .reaction(Reactions.LIKE)
                .reactedAt(LocalDateTime.now())
                .build();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.of(reaction));

        storyService.deleteReaction(storyId);

        verify(storyReactionRepository).delete(reaction);
    }


    @Test
    void deleteReaction_storyExpired() {
        Story story = validStory();
        story.setExpireAt(LocalDateTime.now().minusMinutes(1));

        StoryReaction reaction = StoryReaction.builder()
                .story(story)
                .userId(userId)
                .reaction(Reactions.LIKE)
                .reactedAt(LocalDateTime.now())
                .build();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.of(reaction));

        assertThrows(StoryIsNotAviableByTimeException.class,
                () -> storyService.deleteReaction(storyId));
    }


    @Test
    void deleteReaction_reactionNotFound() {
        Story story = validStory();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build()
        );
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.empty());

        assertThrows(StoryNotFoundException.class,
                () -> storyService.deleteReaction(storyId));
    }


    @Test
    void getReactionStats_success() {
        when(storyReactionRepository.countReactionsByStory(storyId))
                .thenReturn(List.of(
                        new Object[]{Reactions.LIKE, 3L},
                        new Object[]{Reactions.FIRE, 1L}
                ));

        var result = storyService.getReactionStats(storyId);

        assertEquals(2, result.size());
        assertEquals(Reactions.LIKE, result.get(0).getReaction());
        assertEquals(3L, result.get(0).getCount());
    }


    @Test
    void getReactionStats_empty() {
        when(storyReactionRepository.countReactionsByStory(storyId))
                .thenReturn(List.of());

        var result = storyService.getReactionStats(storyId);

        assertTrue(result.isEmpty());
    }


    @Test
    void getReactionStats_repositoryCalled() {
        when(storyReactionRepository.countReactionsByStory(storyId))
                .thenReturn(List.of());

        storyService.getReactionStats(storyId);

        verify(storyReactionRepository).countReactionsByStory(storyId);
    }







}

