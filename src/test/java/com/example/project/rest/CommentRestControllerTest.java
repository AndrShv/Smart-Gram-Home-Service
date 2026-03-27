package com.example.project.rest;

import com.example.project.dto.comment.CommentDTO;
import com.example.project.entity.Comment;
import com.example.project.enums.CommentReactions;
import com.example.project.interfaces.CommentCrudService;
import com.example.project.mappers.CommentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentRestController.class)
class CommentRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommentCrudService commentService;

    @MockBean
    private CommentMapper commentMapper;

    private UUID userId;
    private UUID postId;
    private UUID commentId;
    private Comment comment;
    private CommentDTO commentDTO;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        commentId = UUID.randomUUID();

        comment = Comment.builder()
                .id(commentId)
                .userId(userId)
                .text("Test comment")
                .postId(postId)
                .reactions(new ArrayList<>())
                .childComments(new ArrayList<>())
                .build();

        commentDTO = CommentDTO.builder()
                .id(commentId)
                .userId(userId)
                .text("Test comment")
                .postId(postId)
                .reactions(new ArrayList<>())
                .replies(new ArrayList<>())
                .build();
    }

    // ============================================
    // CREATE COMMENT
    // ============================================

    @Test
    @WithMockUser
    void createComment_success() throws Exception {
        CommentDTO requestDTO = CommentDTO.builder()
                .text("New comment")
                .postId(postId)
                .build();

        when(commentService.createComment(any(CommentDTO.class))).thenReturn(comment);
        when(commentMapper.toDto(comment)).thenReturn(commentDTO);

        mockMvc.perform(post("/api/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.text").value("Test comment"));

        verify(commentService).createComment(any(CommentDTO.class));
    }

    @Test
    @WithMockUser
    void createComment_invalidRequest() throws Exception {
        CommentDTO invalidDTO = new CommentDTO();

        mockMvc.perform(post("/api/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDTO)))
                .andExpect(status().isCreated());
    }

    // ============================================
    // UPDATE COMMENT
    // ============================================

    @Test
    @WithMockUser
    void updateComment_success() throws Exception {
        CommentDTO updateDTO = CommentDTO.builder()
                .text("Updated text")
                .build();

        comment.setText("Updated text");
        commentDTO.setText("Updated text");

        when(commentService.updateComment(eq(commentId), any(CommentDTO.class))).thenReturn(comment);
        when(commentMapper.toDto(comment)).thenReturn(commentDTO);

        mockMvc.perform(put("/api/comments/{commentId}", commentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Updated text"));

        verify(commentService).updateComment(eq(commentId), any(CommentDTO.class));
    }

    @Test
    @WithMockUser
    void updateComment_notFound() throws Exception {
        CommentDTO updateDTO = CommentDTO.builder()
                .text("Updated")
                .build();

        when(commentService.updateComment(eq(commentId), any(CommentDTO.class)))
                .thenThrow(new RuntimeException("Comment not found"));

        mockMvc.perform(put("/api/comments/{commentId}", commentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().is5xxServerError());
    }

    // ============================================
    // DELETE COMMENT
    // ============================================

    @Test
    @WithMockUser
    void deleteComment_success() throws Exception {
        doNothing().when(commentService).deleteComment(commentId);

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentService).deleteComment(commentId);
    }

    @Test
    @WithMockUser
    void deleteComment_notFound() throws Exception {
        doThrow(new RuntimeException("Comment not found"))
                .when(commentService).deleteComment(commentId);

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .with(csrf()))
                .andExpect(status().is5xxServerError());
    }

    // ============================================
    // GET COMMENT
    // ============================================

    @Test
    @WithMockUser
    void getComment_success() throws Exception {
        when(commentService.getComment(commentId)).thenReturn(comment);
        when(commentMapper.toDto(comment)).thenReturn(commentDTO);

        mockMvc.perform(get("/api/comments/{commentId}", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.text").value("Test comment"));

        verify(commentService).getComment(commentId);
    }

    @Test
    @WithMockUser
    void getComment_notFound() throws Exception {
        when(commentService.getComment(commentId))
                .thenThrow(new RuntimeException("Comment not found"));

        mockMvc.perform(get("/api/comments/{commentId}", commentId))
                .andExpect(status().is5xxServerError());
    }

    // ============================================
    // GET COMMENTS BY POST
    // ============================================

    @Test
    @WithMockUser
    void getCommentsByPost_success() throws Exception {
        List<Comment> comments = List.of(comment);
        List<CommentDTO> commentDTOs = List.of(commentDTO);

        when(commentService.getCommentsByPost(postId)).thenReturn(comments);
        when(commentMapper.toDto(comment)).thenReturn(commentDTO);

        mockMvc.perform(get("/api/comments/post/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(commentId.toString()));

        verify(commentService).getCommentsByPost(postId);
    }

    @Test
    @WithMockUser
    void getCommentsByPost_empty() throws Exception {
        when(commentService.getCommentsByPost(postId)).thenReturn(List.of());

        mockMvc.perform(get("/api/comments/post/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ============================================
    // GET COMMENTS BY USER
    // ============================================

    @Test
    @WithMockUser
    void getCommentsByUser_success() throws Exception {
        List<Comment> comments = List.of(comment);

        when(commentService.getCommentsByUser(postId)).thenReturn(comments);
        when(commentMapper.toDto(comment)).thenReturn(commentDTO);

        mockMvc.perform(get("/api/comments/user/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(commentId.toString()));

        verify(commentService).getCommentsByUser(postId);
    }

    // ============================================
    // REACT TO COMMENT
    // ============================================

    @Test
    @WithMockUser
    void reactToComment_success() throws Exception {
        doNothing().when(commentService).reactToComment(commentId, userId, CommentReactions.LIKE);

        mockMvc.perform(post("/api/comments/{commentId}/react", commentId)
                        .with(csrf())
                        .param("userId", userId.toString())
                        .param("reaction", "LIKE"))
                .andExpect(status().isOk());

        verify(commentService).reactToComment(commentId, userId, CommentReactions.LIKE);
    }

    @Test
    @WithMockUser
    void reactToComment_invalidReaction() throws Exception {
        mockMvc.perform(post("/api/comments/{commentId}/react", commentId)
                        .with(csrf())
                        .param("userId", userId.toString())
                        .param("reaction", "INVALID"))
                .andExpect(status().is4xxClientError());
    }

    // ============================================
    // REMOVE REACTION FROM COMMENT
    // ============================================

    @Test
    @WithMockUser
    void removeReactionFromComment_success() throws Exception {
        doNothing().when(commentService).removeReactionFromComment(commentId, userId);

        mockMvc.perform(delete("/api/comments/{commentId}/react", commentId)
                        .with(csrf())
                        .param("userId", userId.toString()))
                .andExpect(status().isNoContent());

        verify(commentService).removeReactionFromComment(commentId, userId);
    }

    // ============================================
    // ADD COMMENT TO COMMENT (REPLY)
    // ============================================

    @Test
    @WithMockUser
    void addCommentToComment_success() throws Exception {
        UUID parentCommentId = UUID.randomUUID();
        CommentDTO replyDTO = CommentDTO.builder()
                .text("Reply text")
                .postId(postId)
                .build();

        Comment reply = Comment.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .text("Reply text")
                .postId(postId)
                .createdAt(LocalDateTime.now())
                .build();

        CommentDTO replyDtoResponse = CommentDTO.builder()
                .id(reply.getId())
                .text("Reply text")
                .build();

        when(commentService.addCommentToComment(eq(parentCommentId), any(CommentDTO.class)))
                .thenReturn(reply);
        when(commentMapper.toDto(reply)).thenReturn(replyDtoResponse);

        mockMvc.perform(post("/api/comments/{parentCommentId}/reply", parentCommentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Reply text"));

        verify(commentService).addCommentToComment(eq(parentCommentId), any(CommentDTO.class));
    }

    @Test
    @WithMockUser
    void addCommentToComment_parentNotFound() throws Exception {
        UUID parentCommentId = UUID.randomUUID();
        CommentDTO replyDTO = CommentDTO.builder()
                .text("Reply")
                .postId(postId)
                .build();

        when(commentService.addCommentToComment(eq(parentCommentId), any(CommentDTO.class)))
                .thenThrow(new RuntimeException("Parent comment not found"));

        mockMvc.perform(post("/api/comments/{parentCommentId}/reply", parentCommentId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replyDTO)))
                .andExpect(status().is5xxServerError());
    }
}