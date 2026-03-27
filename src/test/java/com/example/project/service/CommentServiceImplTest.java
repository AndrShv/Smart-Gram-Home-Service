package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.PostClient;
import com.example.project.dto.comment.CommentDTO;
import com.example.project.dto.post.PostDTO;
import com.example.project.dto.user.UserResponseDTO;
import com.example.project.entity.Comment;
import com.example.project.enums.CommentReactions;
import com.example.project.exceptions.*;
import com.example.project.repository.CommentRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceImplTest {

    @Mock private AuthClient authClient;
    @Mock private PostClient postClient;
    @Mock private CommentRepository commentRepository;
    @Mock private NotificationProducer notificationProducer;

    @InjectMocks
    private CommentServiceImpl commentService;

    private UUID userId;
    private UUID postId;
    private UUID commentId;
    private UserResponseDTO currentUser;
    private PostDTO post;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        commentId = UUID.randomUUID();

        currentUser = UserResponseDTO.builder()
                .id(userId.toString())
                .username("testuser")
                .role("USER")
                .build();

        post = PostDTO.builder()
                .id(postId)
                .userId(userId)
                .build();

        mockAuthentication();
        lenient().when(authClient.getCurrentUser()).thenReturn(currentUser);
        lenient().when(postClient.getPost(any(UUID.class))).thenReturn(post);
    }

    // ============================================
    // CREATE COMMENT
    // ============================================

    @Test
    void createComment_success() {
        CommentDTO dto = buildCommentDTO("Test comment", postId);
        Comment savedComment = buildComment(commentId, userId, "Test comment", postId);

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        Comment result = commentService.createComment(dto);

        assertNotNull(result);
        assertEquals("Test comment", result.getText());
        assertEquals(userId, result.getUserId());
        assertEquals(postId, result.getPostId());
        verify(commentRepository).save(any(Comment.class));
        verify(notificationProducer).sendCreateCommentInPostToPostOwner(
                eq(postId.toString()),
                eq(userId.toString()),
                any(String.class),
                eq("testuser")
        );
    }

    @Test
    void createComment_unauthorized() {
        SecurityContextHolder.clearContext();
        CommentDTO dto = buildCommentDTO("Test", postId);

        assertThrows(UnauthorizedException.class, () -> commentService.createComment(dto));
    }

    @Test
    void createComment_emptyText() {
        CommentDTO dto = buildCommentDTO("", postId);

        assertThrows(UnavailableTextForCommentException.class, () -> commentService.createComment(dto));
    }

    @Test
    void createComment_nullText() {
        CommentDTO dto = buildCommentDTO(null, postId);

        assertThrows(UnavailableTextForCommentException.class, () -> commentService.createComment(dto));
    }

    @Test
    void createComment_postNotFound() {
        when(postClient.getPost(postId)).thenReturn(null);
        CommentDTO dto = buildCommentDTO("Test", postId);

        assertThrows(PostNotFoundException.class, () -> commentService.createComment(dto));
    }

    @Test
    void createComment_trimsWhitespace() {
        CommentDTO dto = buildCommentDTO("  Test comment  ", postId);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment result = commentService.createComment(dto);

        assertEquals("Test comment", result.getText());
    }

    // ============================================
    // UPDATE COMMENT
    // ============================================

    @Test
    void updateComment_success() {
        Comment existingComment = buildComment(commentId, userId, "Old text", postId);
        CommentDTO updateDTO = buildCommentDTO("New text", postId);

        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(existingComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(existingComment);

        Comment result = commentService.updateComment(commentId, updateDTO);

        assertEquals("New text", result.getText());
        verify(commentRepository).save(existingComment);
    }

    @Test
    void updateComment_unauthorized() {
        CommentDTO dto = buildCommentDTO("New text", postId);
        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> commentService.updateComment(commentId, dto));
    }

    @Test
    void updateComment_asAdmin_success() {
        currentUser.setRole("ADMIN");
        UUID anotherUserId = UUID.randomUUID();
        Comment existingComment = buildComment(commentId, anotherUserId, "Old text", postId);
        CommentDTO updateDTO = buildCommentDTO("New text", postId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(existingComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(existingComment);

        Comment result = commentService.updateComment(commentId, updateDTO);

        assertEquals("New text", result.getText());
    }

    @Test
    void updateComment_commentNotFound() {
        CommentDTO dto = buildCommentDTO("New text", postId);
        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> commentService.updateComment(commentId, dto));
    }

    @Test
    void updateComment_emptyText() {
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentDTO dto = CommentDTO.builder()
                .text("")
                .postId(postId)
                .build();

        UserResponseDTO currentUser = UserResponseDTO.builder()
                .id(userId.toString())
                .username("testuser")
                .role("USER")
                .build();

        when(commentService.getAuthenticatedUser()).thenReturn(currentUser);

        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(true);

        Comment existingComment = Comment.builder()
                .id(commentId)
                .userId(userId)
                .text("old text")
                .postId(postId)
                .build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(existingComment));

        assertThrows(UnavailableTextForCommentException.class,
                () -> commentService.updateComment(commentId, dto));
    }
    @Test
    void updateComment_trimsWhitespace() {
        Comment existingComment = buildComment(commentId, userId, "Old", postId);
        CommentDTO dto = buildCommentDTO("  New text  ", postId);

        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(existingComment));
        when(commentRepository.save(any(Comment.class))).thenReturn(existingComment);

        Comment result = commentService.updateComment(commentId, dto);

        assertEquals("New text", result.getText());
    }

    // ============================================
    // DELETE COMMENT
    // ============================================

    @Test
    void deleteComment_success() {
        Comment comment = buildComment(commentId, userId, "Test", postId);

        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deleteComment(commentId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_unauthorized() {
        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> commentService.deleteComment(commentId));
    }

    @Test
    void deleteComment_asAdmin_success() {
        currentUser.setRole("ADMIN");
        UUID anotherUserId = UUID.randomUUID();
        Comment comment = buildComment(commentId, anotherUserId, "Test", postId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        commentService.deleteComment(commentId);

        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_commentNotFound() {
        when(commentRepository.existsByIdAndUserId(commentId, userId)).thenReturn(true);
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> commentService.deleteComment(commentId));
    }

    // ============================================
    // GET COMMENT
    // ============================================

    @Test
    void getComment_success() {
        Comment comment = buildComment(commentId, userId, "Test", postId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        Comment result = commentService.getComment(commentId);

        assertNotNull(result);
        assertEquals(commentId, result.getId());
        assertEquals("Test", result.getText());
    }

    @Test
    void getComment_notFound() {
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> commentService.getComment(commentId));
    }

    // ============================================
    // GET COMMENTS BY POST
    // ============================================

    @Test
    void getCommentsByPost_success() {
        List<Comment> comments = List.of(
                buildComment(UUID.randomUUID(), userId, "Comment 1", postId),
                buildComment(UUID.randomUUID(), userId, "Comment 2", postId)
        );

        when(commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId))
                .thenReturn(comments);

        List<Comment> result = commentService.getCommentsByPost(postId);

        assertEquals(2, result.size());
        verify(postClient).getPost(postId);
    }

    @Test
    void getCommentsByPost_empty() {
        when(commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId))
                .thenReturn(List.of());

        assertThrows(CommentByPostNotFoundException.class,
                () -> commentService.getCommentsByPost(postId));
    }

    @Test
    void getCommentsByPost_postNotFound() {
        when(postClient.getPost(postId)).thenReturn(null);

        assertThrows(PostNotFoundException.class,
                () -> commentService.getCommentsByPost(postId));
    }

    @Test
    void getCommentsByPost_orderedByCreatedAt() {
        Comment comment1 = buildComment(UUID.randomUUID(), userId, "First", postId);
        comment1.setCreatedAt(LocalDateTime.now().minusHours(2));
        Comment comment2 = buildComment(UUID.randomUUID(), userId, "Second", postId);
        comment2.setCreatedAt(LocalDateTime.now().minusHours(1));

        when(commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId))
                .thenReturn(List.of(comment1, comment2));

        List<Comment> result = commentService.getCommentsByPost(postId);

        assertTrue(result.get(0).getCreatedAt().isBefore(result.get(1).getCreatedAt()));
    }

    // ============================================
    // GET COMMENTS BY USER
    // ============================================

    @Test
    void getCommentsByUser_asAdmin() {
        currentUser.setRole("ADMIN");
        List<Comment> comments = List.of(buildComment(commentId, userId, "Test", postId));

        when(commentRepository.findAllCommentsByPostId(postId))
                .thenReturn(Optional.of(comments));

        List<Comment> result = commentService.getCommentsByUser(postId);

        assertEquals(1, result.size());
        verify(commentRepository).findAllCommentsByPostId(postId);
    }

    @Test
    void getCommentsByUser_asRegularUser() {
        List<Comment> comments = List.of(buildComment(commentId, userId, "Test", postId));

        when(commentRepository.findAllCommentsByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.of(comments));

        List<Comment> result = commentService.getCommentsByUser(postId);

        assertEquals(1, result.size());
        verify(commentRepository).findAllCommentsByPostIdAndUserId(postId, userId);
    }

    @Test
    void getCommentsByUser_postNotFound() {
        when(postClient.getPost(postId)).thenReturn(null);

        assertThrows(PostNotFoundException.class,
                () -> commentService.getCommentsByUser(postId));
    }

    @Test
    void getCommentsByUser_noCommentsFound() {
        when(commentRepository.findAllCommentsByPostIdAndUserId(postId, userId))
                .thenReturn(Optional.empty());

        assertThrows(CommentByPostNotFoundException.class,
                () -> commentService.getCommentsByUser(postId));
    }

    // ============================================
    // REACT TO COMMENT
    // ============================================

    @Test
    void reactToComment_success() {
        Comment comment = buildComment(commentId, userId, "Test", postId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.reactToComment(commentId, userId, CommentReactions.LIKE);

        assertTrue(comment.getReactions().contains(CommentReactions.LIKE));
        verify(commentRepository).save(comment);
        verify(notificationProducer).sendCreateCommentReactionToCommentOwner(
                eq(commentId.toString()),
                eq(userId.toString()),
                eq(userId.toString()),
                eq("testuser"),
                eq(CommentReactions.LIKE)
        );
    }

    @Test
    void reactToComment_commentNotFound() {
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> commentService.reactToComment(commentId, userId, CommentReactions.LIKE));
    }

    @Test
    void reactToComment_nullReactionsList() {
        Comment comment = buildComment(commentId, userId, "Test", postId);
        comment.setReactions(null);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.reactToComment(commentId, userId, CommentReactions.LOVE);

        assertNotNull(comment.getReactions());
        assertTrue(comment.getReactions().contains(CommentReactions.LOVE));
    }

    @Test
    void reactToComment_multipleReactions() {
        Comment comment = buildComment(commentId, userId, "Test", postId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.reactToComment(commentId, userId, CommentReactions.LIKE);
        commentService.reactToComment(commentId, userId, CommentReactions.LOVE);

        assertEquals(2, comment.getReactions().size());
    }

    // ============================================
    // REMOVE REACTION FROM COMMENT
    // ============================================

    @Test
    void removeReactionFromComment_success() {
        Comment comment = buildComment(commentId, userId, "Test", postId);
        comment.getReactions().add(CommentReactions.LIKE);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.removeReactionFromComment(commentId, userId);

        assertTrue(comment.getReactions().isEmpty());
        verify(commentRepository).save(comment);
    }

    @Test
    void removeReactionFromComment_commentNotFound() {
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> commentService.removeReactionFromComment(commentId, userId));
    }

    @Test
    void removeReactionFromComment_alreadyEmpty() {
        Comment comment = buildComment(commentId, userId, "Test", postId);
        comment.setReactions(new ArrayList<>());

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.removeReactionFromComment(commentId, userId);

        assertTrue(comment.getReactions().isEmpty());
    }

    // ============================================
    // ADD COMMENT TO COMMENT (REPLY)
    // ============================================

    @Test
    void addCommentToComment_success() {
        Comment parentComment = buildComment(UUID.randomUUID(), userId, "Parent", postId);
        CommentDTO replyDTO = buildCommentDTO("Reply text", postId);

        when(commentRepository.findById(parentComment.getId())).thenReturn(Optional.of(parentComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment result = commentService.addCommentToComment(parentComment.getId(), replyDTO);

        assertNotNull(result);
        assertEquals("Reply text", result.getText());
        assertEquals(parentComment, result.getParentComment());
        verify(notificationProducer).sendCreateCommentInPostToCommentOwner(
                any(String.class),
                eq(postId.toString()),
                eq(userId.toString()),
                eq(userId.toString()),
                eq("testuser")
        );
    }

    @Test
    void addCommentToComment_parentNotFound() {
        CommentDTO replyDTO = buildCommentDTO("Reply", postId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class,
                () -> commentService.addCommentToComment(commentId, replyDTO));
    }

    @Test
    void addCommentToComment_differentPost() {
        UUID anotherPostId = UUID.randomUUID();
        Comment parentComment = buildComment(commentId, userId, "Parent", anotherPostId);
        CommentDTO replyDTO = buildCommentDTO("Reply", postId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(parentComment));

        assertThrows(IllegalArgumentException.class,
                () -> commentService.addCommentToComment(commentId, replyDTO));
    }

    @Test
    void addCommentToComment_emptyText() {
        Comment parentComment = buildComment(commentId, userId, "Parent", postId);
        CommentDTO replyDTO = buildCommentDTO("", postId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(parentComment));

        assertThrows(UnavailableTextForCommentException.class,
                () -> commentService.addCommentToComment(commentId, replyDTO));
    }

    @Test
    void addCommentToComment_trimsWhitespace() {
        Comment parentComment = buildComment(commentId, userId, "Parent", postId);
        CommentDTO replyDTO = buildCommentDTO("  Reply text  ", postId);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(parentComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Comment result = commentService.addCommentToComment(commentId, replyDTO);

        assertEquals("Reply text", result.getText());
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private void mockAuthentication() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);

        SecurityContextHolder.setContext(securityContext);
    }

    private CommentDTO buildCommentDTO(String text, UUID postId) {
        return CommentDTO.builder()
                .text(text)
                .postId(postId)
                .build();
    }

    private Comment buildComment(UUID id, UUID userId, String text, UUID postId) {
        return Comment.builder()
                .id(id)
                .userId(userId)
                .text(text)
                .postId(postId)
                .createdAt(LocalDateTime.now())
                .reactions(new ArrayList<>())
                .childComments(new ArrayList<>())
                .build();
    }
}
