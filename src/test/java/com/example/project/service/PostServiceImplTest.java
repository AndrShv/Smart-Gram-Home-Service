package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.post.PostDTO;
import com.example.project.dto.profile.SubscriberDTO;
import com.example.project.dto.user.ShortUserDTO;
import com.example.project.dto.user.UserResponseDTO;
import com.example.project.entity.Post;
import com.example.project.entity.PostReaction;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.PostNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.AiImageService;
import com.example.project.metrics.HomeRabbitMetricsService;
import com.example.project.metrics.PostMetricsService;
import com.example.project.repository.PostReactionRepository;
import com.example.project.repository.PostRepository;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostServiceImplTest {

    @Mock
    private AuthClient authClient;
    @Mock
    private SubscriptionClient subscriptionClient;
    @Mock
    private PostRepository postRepository;
    @Mock
    private PostReactionRepository postReactionRepository;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private PostMetricsService postMetrics;
    @Mock
    private HomeRabbitMetricsService rabbitMetrics;
    @Mock
    private ImaggaServiceImpl imaggaService;
    @Mock
    private AiImageService aiImageService;
    @Mock
    private PostAsyncService postAsyncService;

    @InjectMocks
    private PostServiceImpl postService;

    private UUID postId;
    private UUID userId;
    private Post post;

    @BeforeEach
    void setUp() {
        postId = UUID.randomUUID();
        userId = UUID.randomUUID();

        post = Post.builder()
                .id(postId)
                .userId(userId)
                .description("Test Post")
                .photoUrl("http://example.com/photo.jpg")
                .createdAt(LocalDateTime.now())
                .reactions(new ArrayList<>())
                .comments(new ArrayList<>())
                .build();

        Timer mockTimer = mock(Timer.class, withSettings().lenient());
        try {
            when(mockTimer.recordCallable(any())).thenAnswer(inv ->
                    inv.<java.util.concurrent.Callable<?>>getArgument(0).call()
            );
        } catch (Exception ignored) {
        }

        lenient().when(postMetrics.createPostTimer()).thenReturn(mockTimer);
        lenient().when(postMetrics.getPostTimer()).thenReturn(mockTimer);

        // ✅ мокаем SecurityContext
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        // ✅ подписчики пустые по умолчанию — не ломаем тесты которым не важны уведомления
        lenient().when(subscriptionClient.getFollowers(any())).thenReturn(List.of());
    }

    // ============================================
    //  CREATE POST
    // ============================================

    @Test
    void createPost_success() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setDescription("Test Post");
        dto.setPhotoUrl("http://example.com/photo.jpg");

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).username("testuser").build());
        when(postRepository.save(any(Post.class))).thenReturn(post);

        Post created = postService.createPost(dto, userId);

        assertNotNull(created);
        assertEquals("Test Post", created.getDescription());
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void createPost_unauthorized_throws() {
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        when(auth.isAuthenticated()).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> postService.createPost(new PostDTO(), userId));
    }

    @Test
    void createPost_sendsNotificationsToSubscribers() throws Exception {
        PostDTO dto = new PostDTO();
        dto.setDescription("Post");

        UUID subscriberId = UUID.randomUUID();

        SubscriberDTO sub = SubscriberDTO.builder()
                .subscriberUserId(subscriberId)
                .build();

        ShortUserDTO currentUser = new ShortUserDTO();
        currentUser.setUsername("testuser");

        when(authClient.getUserById(userId)).thenReturn(currentUser);
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postService.createPost(dto, userId);

        verify(postAsyncService).sendNotificationsAsync(
                eq(post),
                eq(userId),
                eq("testuser")
        );
    }

    // ============================================
    //  GET POST
    // ============================================

    @Test
    void getPostById_success() throws Exception {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        assertEquals(postId, postService.getPostById(postId).getId());
    }

    @Test
    void getPostById_notFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        assertThrows(PostNotFoundException.class, () -> postService.getPostById(postId));
    }

    // ============================================
    //  UPDATE POST
    // ============================================

    @Test
    void updatePost_success() {
        PostDTO dto = new PostDTO();
        dto.setDescription("Updated");
        dto.setPhotoUrl("http://example.com/updated.jpg");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        postService.updatePost(postId, dto);

        assertEquals("Updated", post.getDescription());
        verify(postRepository).save(post);
        verify(postMetrics).incrementUpdated();
    }

    @Test
    void updatePost_notFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        assertThrows(PostNotFoundException.class, () -> postService.updatePost(postId, new PostDTO()));
    }

    // ============================================
    //  DELETE POST
    // ============================================

    @Test
    void deletePost_success() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        postService.deletePost(postId);
        verify(postRepository).delete(post);
        verify(postMetrics).incrementDeleted();
    }

    @Test
    void deletePost_notFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        assertThrows(PostNotFoundException.class, () -> postService.deletePost(postId));
    }

    // ============================================
    //  REACT TO POST
    // ============================================

    @Test
    void reactToPost_newReaction() {
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).username("testuser").build());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

        postService.reactToPost(postId, Reactions.LIKE);

        verify(postReactionRepository).save(any(PostReaction.class));
        verify(postMetrics).incrementReactionAdded();
    }

    @Test
    void reactToPost_updateExistingReaction() {
        PostReaction existingReaction = PostReaction.builder()
                .reaction(Reactions.LOVE).reactedAt(LocalDateTime.now()).build();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).username("testuser").build());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(existingReaction));

        postService.reactToPost(postId, Reactions.LIKE);

        assertEquals(Reactions.LIKE, existingReaction.getReaction());
        verify(postReactionRepository, never()).save(any());
        verify(postMetrics).incrementReactionAdded();
    }

    @Test
    void reactToPost_sendsNotificationToPostOwner() {
        UUID anotherUser = UUID.randomUUID();
        Post ownedPost = Post.builder().id(postId).userId(anotherUser).build();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).username("actor").build());
        when(postRepository.findById(postId)).thenReturn(Optional.of(ownedPost));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

        postService.reactToPost(postId, Reactions.LIKE);

        verify(notificationProducer).sendPostLiked(
                postId.toString(),
                userId.toString(),
                anotherUser.toString(),
                "actor"
        );
    }

    // ============================================
    //  DELETE REACTION
    // ============================================

    @Test
    void deleteReaction_success() {
        PostReaction reaction = PostReaction.builder().build();
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(reaction));

        postService.deleteReaction(postId);

        verify(postReactionRepository).delete(reaction);
        verify(postMetrics).incrementReactionDeleted();
    }

    @Test
    void deleteReaction_notFound() {
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder().id(userId.toString()).build());
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.deleteReaction(postId));
    }

    // ============================================
    //  GET ALL POSTS
    // ============================================

    @Test
    void getAllPosts_returnsSortedList() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of(post));
        assertEquals(1, postService.getAllPosts().size());
    }

    @Test
    void getAllPosts_emptyList() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of());
        assertTrue(postService.getAllPosts().isEmpty());
    }

    // ============================================
    //  GET POSTS BY USER ID
    // ============================================

    @Test
    void getPostsByUserId_success() {
        when(postRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(post));
        assertEquals(1, postService.getPostsByUserId(userId).size());
    }

    @Test
    void getPostsByUserId_empty() {
        when(postRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        assertTrue(postService.getPostsByUserId(userId).isEmpty());
    }

    // ============================================
    //  COUNT POSTS BY USER ID
    // ============================================

    @Test
    void countPostsByUserId_success() {
        when(postRepository.countByUserId(userId)).thenReturn(5L);
        assertEquals(5, postService.countPostsByUserId(userId));
    }

    @Test
    void countPostsByUserId_zero() {
        when(postRepository.countByUserId(userId)).thenReturn(0L);
        assertEquals(0, postService.countPostsByUserId(userId));
    }

    // ============================================
    //  IS POST OWNER
    // ============================================

    @Test
    void isPostOwner_true() throws Exception {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        assertTrue(postService.isPostOwner(postId, userId));
    }

    @Test
    void isPostOwner_false() throws Exception {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        assertFalse(postService.isPostOwner(postId, UUID.randomUUID()));
    }

    // ============================================
    //  COUNT REACTIONS
    // ============================================

    @Test
    void countReactions_success() {
        when(postReactionRepository.countByPostId(postId)).thenReturn(3L);
        assertEquals(3, postService.countReactions(postId));
    }

    @Test
    void countReactions_zero() {
        when(postReactionRepository.countByPostId(postId)).thenReturn(0L);
        assertEquals(0, postService.countReactions(postId));
    }

    // ============================================
    //  GET REACTIONS BY POST ID
    // ============================================

    @Test
    void getReactionsByPostId_success() {
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of(PostReaction.builder().build()));
        assertEquals(1, postService.getReactionsByPostId(postId).size());
    }

    @Test
    void getReactionsByPostId_empty() {
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of());
        assertTrue(postService.getReactionsByPostId(postId).isEmpty());
    }

    // ============================================
    //  GET REACTION STATS
    // ============================================

    @Test
    void getReactionStats_groupingWorks() {
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of(
                PostReaction.builder().reaction(Reactions.LIKE).build(),
                PostReaction.builder().reaction(Reactions.LIKE).build(),
                PostReaction.builder().reaction(Reactions.LOVE).build()
        ));
        var stats = postService.getReactionStats(postId);
        assertEquals(2, stats.size());
        assertEquals(2, stats.get(0).getCount());
    }

    @Test
    void getReactionStats_empty() {
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of());
        assertTrue(postService.getReactionStats(postId).isEmpty());
    }

    // ============================================
    //  HAS USER REACTED
    // ============================================

    @Test
    void hasUserReacted_true() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(PostReaction.builder().build()));
        assertTrue(postService.hasUserReacted(postId, userId));
    }

    @Test
    void hasUserReacted_false() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());
        assertFalse(postService.hasUserReacted(postId, userId));
    }

    // ============================================
    //  GET USER REACTION
    // ============================================

    @Test
    void getUserReaction_present() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(PostReaction.builder().build()));
        assertTrue(postService.getUserReaction(postId, userId).isPresent());
    }

    @Test
    void getUserReaction_empty() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());
        assertTrue(postService.getUserReaction(postId, userId).isEmpty());
    }

    // ============================================
    //  COUNT REACTIONS BY TYPE
    // ============================================

    @Test
    void countReactionsByType_success() {
        when(postReactionRepository.countByPostIdAndReaction(postId, Reactions.LIKE)).thenReturn(4L);
        assertEquals(4, postService.countReactionsByType(postId, Reactions.LIKE));
    }

    @Test
    void countReactionsByType_zero() {
        when(postReactionRepository.countByPostIdAndReaction(postId, Reactions.LIKE)).thenReturn(0L);
        assertEquals(0, postService.countReactionsByType(postId, Reactions.LIKE));
    }
}