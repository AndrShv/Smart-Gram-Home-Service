package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.profile.SubscriberDTO;
import com.example.project.dto.story.StoryDTO;
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
import com.example.project.metrics.HomeRabbitMetricsService;
import com.example.project.metrics.StoryMetricsService;
import com.example.project.repository.StoryReactionRepository;
import com.example.project.repository.StoryRepository;
import com.example.project.repository.StoryViewerRepository;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceImplTest {

    @Mock private AuthClient authClient;
    @Mock private SubscriptionClient subscriptionClient;
    @Mock private StoryRepository storyRepository;
    @Mock private StoryReactionRepository storyReactionRepository;
    @Mock private StoryViewerRepository storyViewerRepository;
    @Mock private NotificationProducer notificationProducer;
    @Mock private StoryMetricsService storyMetrics;
    @Mock private HomeRabbitMetricsService rabbitMetrics;
    @Mock private ImaggaService imaggaService;
    @Mock private AiImageService aiImageService;
    @Mock private StoryAsyncService storyAsyncService;

    @InjectMocks
    private StoryServiceImpl storyService;

    private UUID userId;
    private UUID storyId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storyId = UUID.randomUUID();

        Timer mockTimer = mock(Timer.class, withSettings().lenient());
        try {
            when(mockTimer.recordCallable(any())).thenAnswer(inv ->
                    inv.<java.util.concurrent.Callable<?>>getArgument(0).call()
            );
        } catch (Exception ignored) {}

        lenient().when(storyMetrics.createStoryTimer()).thenReturn(mockTimer);
        lenient().when(subscriptionClient.getFollowers(any())).thenReturn(List.of());
        lenient().when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));

        lenient().doNothing().when(storyAsyncService).createStoryAsync(any(Story.class), any(UUID.class));

        lenient().when(storyAsyncService.sendNotificationsAsync(any(Story.class), any(UUID.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        mockAuthenticated();
    }
    // ============================================
    //  CREATE STORY
    // ============================================

    @Test
    void createStory_success() throws Exception {
        StoryDTO dto = buildDto(StoryCategory.FAMILY, StoryMood.HAPPY);
        dto.setDescription("Test story");
        dto.setPhotoUrl("http://example.com/photo.jpg");
        dto.setPublic(true);
        dto.setLocation("Odesa");

        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        Story result = storyService.createStory(dto);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("Test story", result.getDescription());
        assertEquals("http://example.com/photo.jpg", result.getPhotoUrl());

        verify(storyRepository).save(any(Story.class));
        verify(storyAsyncService).createStoryAsync(any(Story.class), eq(userId));
        verify(storyAsyncService).sendNotificationsAsync(any(Story.class), eq(userId), eq("testuser"));
    }

    @Test
    void createStory_unauthorized() {
        SecurityContextHolder.clearContext();

        assertThrows(UnauthorizedException.class,
                () -> storyService.createStory(new StoryDTO()));
    }

    @Test
    void createStory_expireTimeIs24Hours() throws Exception {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        StoryDTO dto = buildDto(StoryCategory.FRIENDS, StoryMood.HAPPY);

        Story story = storyService.createStory(dto);

        assertNotNull(story.getCreatedAt());
        assertNotNull(story.getExpireAt());
        assertTrue(story.getExpireAt().isAfter(story.getCreatedAt()));
    }

    @Test
    void createStory_viewersEmpty() throws Exception {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        StoryDTO dto = buildDto(StoryCategory.FRIENDS, StoryMood.HAPPY);

        Story story = storyService.createStory(dto);

        assertNotNull(story.getViewers());
        assertTrue(story.getViewers().isEmpty());
    }

    @Test
    void createStory_descriptionSaved() throws Exception {
        StoryDTO dto = buildDto(StoryCategory.FRIENDS, StoryMood.HAPPY);
        dto.setDescription("hello");

        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        assertEquals("hello", storyService.createStory(dto).getDescription());
    }

    @Test
    void createStory_photoUrlSaved() throws Exception {
        StoryDTO dto = buildDto(StoryCategory.FRIENDS, StoryMood.HAPPY);
        dto.setPhotoUrl("img.png");

        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        assertEquals("img.png", storyService.createStory(dto).getPhotoUrl());
    }

    @Test
    void createStory_saveCalledOnce() throws Exception {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        StoryDTO dto = buildDto(StoryCategory.FRIENDS, StoryMood.HAPPY);

        storyService.createStory(dto);

        verify(storyRepository, times(1)).save(any());
    }

    @Test
    void createStory_sendsNotificationsToSubscribers() throws Exception {
        UUID subscriberId = UUID.randomUUID();
        SubscriberDTO sub = SubscriberDTO.builder()
                .subscriberUserId(subscriberId)
                .build();

        when(subscriptionClient.getFollowers(userId)).thenReturn(List.of(sub));
        when(authClient.getCurrentUser()).thenReturn(user());
        when(authClient.getUserById(userId)).thenReturn(shortUser("testuser"));

        Story saved = validStory();
        when(storyRepository.save(any())).thenReturn(saved);

        when(storyAsyncService.sendNotificationsAsync(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        StoryDTO dto = buildDto(StoryCategory.FRIENDS, StoryMood.HAPPY);
        Story story = storyService.createStory(dto);

        verify(storyAsyncService).sendNotificationsAsync(
                eq(story),
                eq(userId),
                eq("testuser")
        );
    }

    // ============================================
    //  VIEW STORY
    // ============================================

    @Test
    void viewStory_success() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(validStory()));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository).save(any(StoryViewer.class));
        verify(storyMetrics).incrementViewed();
    }

    @Test
    void viewStory_storyNotFound() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThrows(StoryNotFoundException.class,
                () -> storyService.viewStory(storyId, userId));
    }

    @Test
    void viewStory_storyExpired() {
        Story story = validStory();
        story.setExpireAt(LocalDateTime.now().minusMinutes(1));

        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

        assertThrows(StoryIsNotAviableByTimeException.class,
                () -> storyService.viewStory(storyId, userId));
    }

    @Test
    void viewStory_alreadyViewed() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(validStory()));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(true);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository, never()).save(any());
    }

    @Test
    void viewStory_viewerIdCorrect() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(validStory()));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository).save(argThat(v -> v.getViewerId().equals(userId)));
    }

    @Test
    void viewStory_viewedAtSet() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(validStory()));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository).save(argThat(v -> v.getViewedAt() != null));
    }

    @Test
    void viewStory_storyLinked() {
        Story story = validStory();
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(storyViewerRepository).save(argThat(v -> v.getStory().equals(story)));
    }

    @Test
    void viewStory_sendsNotificationToOwner() {
        UUID anotherUser = UUID.randomUUID();
        Story story = Story.builder()
                .id(storyId)
                .userId(anotherUser)
                .createdAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusHours(1))
                .viewers(new ArrayList<>())
                .build();

        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(false);

        storyService.viewStory(storyId, userId);

        verify(notificationProducer).sendStoryViewed(
                storyId.toString(),
                userId.toString(),
                anotherUser.toString(),
                "testuser"
        );
    }

    // ============================================
    //  HAS VIEWED / COUNT VIEWS
    // ============================================

    @Test
    void hasViewed_true() {
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(true);
        assertTrue(storyService.hasViewed(storyId, userId));
    }

    @Test
    void hasViewed_false() {
        when(storyViewerRepository.existsByStoryIdAndViewerId(storyId, userId)).thenReturn(false);
        assertFalse(storyService.hasViewed(storyId, userId));
    }

    @Test
    void countViews_success() {
        when(storyViewerRepository.countByStoryId(storyId)).thenReturn(5L);
        assertEquals(5L, storyService.countViews(storyId));
    }

    @Test
    void countViews_zero() {
        when(storyViewerRepository.countByStoryId(storyId)).thenReturn(0L);
        assertEquals(0L, storyService.countViews(storyId));
    }

    @Test
    void countViews_repositoryCalled() {
        when(storyViewerRepository.countByStoryId(storyId)).thenReturn(0L);
        storyService.countViews(storyId);
        verify(storyViewerRepository).countByStoryId(storyId);
    }

    // ============================================
    //  REACT TO STORY
    // ============================================

    @Test
    void reactToStory_newReaction_saved() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(validStory()));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId)).thenReturn(Optional.empty());

        storyService.reactToStory(storyId, Reactions.LIKE);

        verify(storyReactionRepository).save(any(StoryReaction.class));
        verify(storyMetrics).incrementReactionAdded();
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

        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.of(existing));

        storyService.reactToStory(storyId, Reactions.FIRE);

        assertEquals(Reactions.FIRE, existing.getReaction());
        assertNotNull(existing.getReactedAt());
        verify(storyReactionRepository, never()).save(any());
        verify(storyMetrics).incrementReactionAdded();
    }

    @Test
    void reactToStory_storyNotFound() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThrows(StoryNotFoundException.class,
                () -> storyService.reactToStory(storyId, Reactions.LIKE));
    }

    @Test
    void reactToStory_reactionFieldsCorrect() {
        Story story = validStory();

        when(authClient.getCurrentUser()).thenReturn(user());
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

    // ============================================
    //  DELETE REACTION
    // ============================================

    @Test
    void deleteReaction_success() {
        Story story = validStory();
        StoryReaction reaction = StoryReaction.builder()
                .story(story)
                .userId(userId)
                .reaction(Reactions.LIKE)
                .reactedAt(LocalDateTime.now())
                .build();

        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.of(reaction));

        storyService.deleteReaction(storyId);

        verify(storyReactionRepository).delete(reaction);
        verify(storyMetrics).incrementReactionDeleted();
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

        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.of(reaction));

        assertThrows(StoryIsNotAviableByTimeException.class,
                () -> storyService.deleteReaction(storyId));
    }

    @Test
    void deleteReaction_reactionNotFound() {
        when(authClient.getCurrentUser()).thenReturn(user());
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(validStory()));
        when(storyReactionRepository.findByStoryIdAndUserId(storyId, userId))
                .thenReturn(Optional.empty());

        assertThrows(StoryNotFoundException.class,
                () -> storyService.deleteReaction(storyId));
    }

    // ============================================
    //  REACTION STATS
    // ============================================

    @Test
    void getReactionStats_success() {
        when(storyReactionRepository.countReactionsByStory(storyId)).thenReturn(List.of(
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
        when(storyReactionRepository.countReactionsByStory(storyId)).thenReturn(List.of());
        assertTrue(storyService.getReactionStats(storyId).isEmpty());
    }

    @Test
    void getReactionStats_repositoryCalled() {
        when(storyReactionRepository.countReactionsByStory(storyId)).thenReturn(List.of());
        storyService.getReactionStats(storyId);
        verify(storyReactionRepository).countReactionsByStory(storyId);
    }

    // ============================================
    //  HELPERS
    // ============================================

    private void mockAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);

        SecurityContextHolder.setContext(securityContext);
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

    private UserResponseDTO user() {
        return UserResponseDTO.builder()
                .id(userId.toString())
                .username("testuser")
                .build();
    }

    private ShortUserDTO shortUser(String username) {
        ShortUserDTO dto = new ShortUserDTO();
        dto.setUsername(username);
        return dto;
    }

    private StoryDTO buildDto(StoryCategory category, StoryMood mood) {
        StoryDTO dto = new StoryDTO();
        dto.setCategory(category.name());
        dto.setMood(mood.name());
        return dto;
    }
}