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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Добавьте эту аннотацию
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
                .viewers(new ArrayList<>())
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
}