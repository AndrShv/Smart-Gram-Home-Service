package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.dto.PostDTO;
import com.example.project.dto.UserResponseDTO;
import com.example.project.entity.Post;
import com.example.project.entity.PostReaction;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.PostNotFoundException;
import com.example.project.exceptions.ReactionNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.repository.PostReactionRepository;
import com.example.project.repository.PostRepository;
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
    private PostRepository postRepository;

    @Mock
    private PostReactionRepository postReactionRepository;

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

        // Mock security context
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    /* ================= CREATE POST ================= */
    @Test
    void createPost_success() {
        PostDTO dto = new PostDTO();
        dto.setDescription("Test Post");
        dto.setPhotoUrl("http://example.com/photo.jpg");

        UserResponseDTO currentUser = new UserResponseDTO();
        currentUser.setId(userId.toString());
        when(authClient.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.save(any(Post.class))).thenReturn(post);

        Post created = postService.createPost(dto);

        assertNotNull(created);
        assertEquals("Test Post", created.getDescription());
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void createPost_unauthorized_throws() {
        UserResponseDTO currentUser = new UserResponseDTO();
        currentUser.setId(userId.toString());
        when(authClient.getCurrentUser()).thenReturn(currentUser);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        when(auth.isAuthenticated()).thenReturn(false);

        PostDTO dto = new PostDTO();
        assertThrows(UnauthorizedException.class, () -> postService.createPost(dto));
    }

    /* ================= GET POST ================= */
    @Test
    void getPostById_success() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        Post found = postService.getPostById(postId);
        assertEquals(postId, found.getId());
    }

    @Test
    void getPostById_notFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        assertThrows(PostNotFoundException.class, () -> postService.getPostById(postId));
    }

    /* ================= UPDATE POST ================= */
    @Test
    void updatePost_success() {
        PostDTO dto = new PostDTO();
        dto.setDescription("Updated");
        dto.setPhotoUrl("http://example.com/updated.jpg");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        postService.updatePost(postId, dto);

        assertEquals("Updated", post.getDescription());
        assertEquals("http://example.com/updated.jpg", post.getPhotoUrl());
        verify(postRepository, times(1)).save(post);
    }

    @Test
    void updatePost_notFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        PostDTO dto = new PostDTO();
        assertThrows(PostNotFoundException.class, () -> postService.updatePost(postId, dto));
    }

    /* ================= DELETE POST ================= */
    @Test
    void deletePost_success() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        postService.deletePost(postId);
        verify(postRepository, times(1)).delete(post);
    }

    @Test
    void deletePost_notFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        assertThrows(PostNotFoundException.class, () -> postService.deletePost(postId));
    }

    /* ================= REACT TO POST ================= */
    @Test
    void reactToPost_newReaction() {
        PostReaction reaction = PostReaction.builder().build();
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder()
                        .id(userId.toString())
                        .username("testuser")
                        .email("test@example.com")
                        .role("USER")
                        .token(null)
                        .build()
        );
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());
        when(postReactionRepository.save(any(PostReaction.class))).thenReturn(reaction);

        postService.reactToPost(postId, Reactions.LIKE);

        verify(postReactionRepository, times(1)).save(any(PostReaction.class));
    }

    @Test
    void reactToPost_updateExistingReaction() {
        PostReaction existingReaction = PostReaction.builder()
                .reaction(Reactions.LOVE)
                .reactedAt(LocalDateTime.now())
                .build();

        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder()
                        .id(userId.toString())
                        .username("testuser")
                        .email("test@example.com")
                        .role("USER")
                        .token(null)
                        .build()
        );
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(existingReaction));

        postService.reactToPost(postId, Reactions.LIKE);

        assertEquals(Reactions.LIKE, existingReaction.getReaction());
        verify(postReactionRepository, never()).save(any(PostReaction.class));
    }

    /* ================= DELETE REACTION ================= */
    @Test
    void deleteReaction_success() {
        PostReaction reaction = PostReaction.builder().build();
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder()
                        .id(userId.toString())
                        .username("testuser")
                        .email("test@example.com")
                        .role("USER")
                        .token(null)
                        .build()
        );
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(reaction));

        postService.deleteReaction(postId);

        verify(postReactionRepository, times(1)).delete(reaction);
    }

    @Test
    void deleteReaction_notFound() {
        when(authClient.getCurrentUser()).thenReturn(
                UserResponseDTO.builder()
                        .id(userId.toString())
                        .username("testuser")
                        .email("test@example.com")
                        .role("USER")
                        .token(null)
                        .build()
        );
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postReactionRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.deleteReaction(postId));
    }


    /* ================= GET ALL POSTS ================= */
    @Test
    void getAllPosts_returnsSortedList() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of(post));

        var result = postService.getAllPosts();

        assertEquals(1, result.size());
        verify(postRepository).findAll(any(Sort.class));
    }

    @Test
    void getAllPosts_emptyList() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of());

        var result = postService.getAllPosts();

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllPosts_repositoryCalledOnce() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of(post));

        postService.getAllPosts();

        verify(postRepository, times(1)).findAll(any(Sort.class));
    }

    /* ================= GET POSTS BY USER ID ================= */
    @Test
    void getPostsByUserId_success() {
        when(postRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(post));

        var result = postService.getPostsByUserId(userId);

        assertEquals(1, result.size());
    }

    @Test
    void getPostsByUserId_empty() {
        when(postRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        assertTrue(postService.getPostsByUserId(userId).isEmpty());
    }

    @Test
    void getPostsByUserId_repositoryCalled() {
        postService.getPostsByUserId(userId);
        verify(postRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    /* ================= GET FEED FOR USER ================= */
    @Test
    void getFeedForUser_returnsAllPosts() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of(post));

        var feed = postService.getFeedForUser(userId);

        assertEquals(1, feed.size());
    }

    @Test
    void getFeedForUser_emptyFeed() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertTrue(postService.getFeedForUser(userId).isEmpty());
    }

    @Test
    void getFeedForUser_callsGetAllPosts() {
        when(postRepository.findAll(any(Sort.class))).thenReturn(List.of(post));

        postService.getFeedForUser(userId);

        verify(postRepository).findAll(any(Sort.class));
    }

    /* ================= COUNT POSTS BY USER ID ================= */
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

    @Test
    void countPostsByUserId_repositoryCalled() {
        postService.countPostsByUserId(userId);
        verify(postRepository).countByUserId(userId);
    }

    /* ================= IS POST OWNER ================= */
    @Test
    void isPostOwner_true() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertTrue(postService.isPostOwner(postId, userId));
    }

    @Test
    void isPostOwner_false() {
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertFalse(postService.isPostOwner(postId, UUID.randomUUID()));
    }

    @Test
    void isPostOwner_postNotFound() {
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class,
                () -> postService.isPostOwner(postId, userId));
    }

    /* ================= COUNT REACTIONS ================= */

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

    /* ================= GET REACTIONS BY POST ID ================= */

    @Test
    void getReactionsByPostId_success() {
        PostReaction r = PostReaction.builder().build();
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of(r));

        assertEquals(1, postService.getReactionsByPostId(postId).size());
    }

    @Test
    void getReactionsByPostId_empty() {
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of());

        assertTrue(postService.getReactionsByPostId(postId).isEmpty());
    }


    /* ================= GET REACTION STATS ================= */

    @Test
    void getReactionStats_groupingWorks() {
        PostReaction r1 = PostReaction.builder().reaction(Reactions.LIKE).build();
        PostReaction r2 = PostReaction.builder().reaction(Reactions.LIKE).build();
        PostReaction r3 = PostReaction.builder().reaction(Reactions.LOVE).build();

        when(postReactionRepository.findByPostId(postId))
                .thenReturn(List.of(r1, r2, r3));

        var stats = postService.getReactionStats(postId);

        assertEquals(2, stats.size());
        assertEquals(2, stats.get(0).getCount());
    }

    @Test
    void getReactionStats_empty() {
        when(postReactionRepository.findByPostId(postId)).thenReturn(List.of());

        assertTrue(postService.getReactionStats(postId).isEmpty());
    }


    /* ================= HAS USER REACTED ================= */
    @Test
    void hasUserReacted_true() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(PostReaction.builder().build()));

        assertTrue(postService.hasUserReacted(postId, userId));
    }

    @Test
    void hasUserReacted_false() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.empty());

        assertFalse(postService.hasUserReacted(postId, userId));
    }



    /* ================= GET USER REACTION ================= */
    @Test
    void getUserReaction_present() {
        PostReaction reaction = PostReaction.builder().build();

        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(reaction));

        assertTrue(postService.getUserReaction(postId, userId).isPresent());
    }

    @Test
    void getUserReaction_empty() {
        when(postReactionRepository.findByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.empty());

        assertTrue(postService.getUserReaction(postId, userId).isEmpty());
    }


    /* ================= COUNT REACTIONS BY TYPE ================= */
    @Test
    void countReactionsByType_success() {
        when(postReactionRepository.countByPostIdAndReaction(postId, Reactions.LIKE))
                .thenReturn(4L);

        assertEquals(4, postService.countReactionsByType(postId, Reactions.LIKE));
    }

    @Test
    void countReactionsByType_zero() {
        when(postReactionRepository.countByPostIdAndReaction(postId, Reactions.LIKE))
                .thenReturn(0L);

        assertEquals(0, postService.countReactionsByType(postId, Reactions.LIKE));
    }




}