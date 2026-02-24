package com.example.project.rest;

import com.example.project.dto.PostDTO;
import com.example.project.dto.PostCreateRequestDTO;
import com.example.project.dto.PostReactionRequestDTO;
import com.example.project.entity.Post;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.PostNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.PostCrudService;
import com.example.project.interfaces.PostReactionService;
import com.example.project.mappers.PostMapper;
import com.example.project.service.MinioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PostRestControllerTest {

    @Mock
    private PostCrudService postService;

    @Mock
    private PostReactionService postReactionService;

    @Mock
    private PostMapper postMapper;

    @InjectMocks
    private PostRestController postRestController;

    @Mock
    private MinioService minioService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID postId;
    private UUID userId;
    private Post post;


    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(postRestController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        postId = UUID.randomUUID();
        userId = UUID.randomUUID();

        post = Post.builder()
                .id(postId)
                .userId(userId)
                .description("Test Post")
                .photoUrl("http://example.com/photo.jpg")
                .createdAt(LocalDateTime.now())
                .build();

        var auth = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }



    @RestControllerAdvice
    static class TestExceptionHandler {

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<String> handleUnauthorized(UnauthorizedException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
        }

        @ExceptionHandler(PostNotFoundException.class)
        public ResponseEntity<String> handlePostNotFound(PostNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
        }
    }

    /* ================= CREATE POST ================= */
    @Test
    void createPost_success() throws Exception {
        when(minioService.uploadPostPhotoBytes(any(byte[].class), anyString(), anyString()))
                .thenReturn("http://minio/posts/photo.jpg");

        when(postService.createPost(any(PostDTO.class))).thenReturn(post);

        when(postMapper.toDto(post)).thenReturn(PostDTO.builder()
                .id(postId)
                .userId(userId)
                .description("Test Post")
                .photoUrl("http://minio/posts/photo.jpg")
                .createdAt(LocalDateTime.now())
                .viewsCount(0)
                .reactionsCount(0)
                .commentsCount(0)
                .build());

        MockMultipartFile photoFile = new MockMultipartFile(
                "photo",
                "photo.jpg",
                "image/jpeg",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/posts")
                        .file(photoFile)
                        .param("description", "Test Post")
                        .param("location", "Одесса")
                        .param("isPublic", "true")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.description").value("Test Post"));

        verify(minioService, times(1)).uploadPostPhotoBytes(any(byte[].class), anyString(), anyString());
        verify(postService, times(1)).createPost(any(PostDTO.class));
        verify(postMapper, times(1)).toDto(post);
    }

    @Test
    void createPost_unauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        MockMultipartFile photoFile = new MockMultipartFile(
                "photo",
                "photo.jpg",
                "image/jpeg",
                "dummy image content".getBytes()
        );

        mockMvc.perform(multipart("/api/posts")
                        .file(photoFile)
                        .param("description", "Test Post")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());
    }

    /* ================= GET POST ================= */

    @Test
    void getPost_success() throws Exception {
        PostDTO responseDTO = PostDTO.builder()
                .id(postId)
                .userId(userId)
                .description("Test Post")
                .photoUrl("http://example.com/photo.jpg")
                .createdAt(LocalDateTime.now())
                .viewsCount(0)
                .reactionsCount(0)
                .commentsCount(0)
                .build();

        when(postService.getPostById(postId)).thenReturn(post);
        when(postMapper.toDto(post)).thenReturn(responseDTO);

        mockMvc.perform(get("/api/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()))
                .andExpect(jsonPath("$.description").value("Test Post"));

        verify(postService, times(1)).getPostById(postId);
        verify(postMapper, times(1)).toDto(post);
    }

    @Test
    void getPost_notFound() throws Exception {
        when(postService.getPostById(postId)).thenThrow(new PostNotFoundException("Пост не найден"));

        mockMvc.perform(get("/api/posts/{postId}", postId))
                .andExpect(status().isNotFound());
    }

    /* ================= UPDATE POST ================= */

    @Test
    void updatePost_success() throws Exception {
        PostDTO postDTO = new PostDTO();
        postDTO.setDescription("Updated Post");
        postDTO.setPhotoUrl("http://example.com/updated.jpg");

        doNothing().when(postService).updatePost(eq(postId), any(PostDTO.class));

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postDTO)))
                .andExpect(status().isOk());

        verify(postService, times(1)).updatePost(eq(postId), any(PostDTO.class));
    }

    @Test
    void updatePost_notFound() throws Exception {
        PostDTO postDTO = new PostDTO();
        postDTO.setDescription("Updated Post");
        postDTO.setPhotoUrl("http://example.com/updated.jpg");

        doThrow(new PostNotFoundException("Пост не найден")).when(postService)
                .updatePost(eq(postId), any(PostDTO.class));

        mockMvc.perform(put("/api/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(postDTO)))
                .andExpect(status().isNotFound());
    }

    /* ================= DELETE POST ================= */

    @Test
    void deletePost_success() throws Exception {
        doNothing().when(postService).deletePost(postId);

        mockMvc.perform(delete("/api/posts/{postId}", postId))
                .andExpect(status().isNoContent());

        verify(postService, times(1)).deletePost(postId);
    }

    @Test
    void deletePost_notFound() throws Exception {
        doThrow(new PostNotFoundException("Пост не найден")).when(postService).deletePost(postId);

        mockMvc.perform(delete("/api/posts/{postId}", postId))
                .andExpect(status().isNotFound());
    }

    /* ================= REACT TO POST ================= */

    @Test
    void reactToPost_success() throws Exception {
        PostReactionRequestDTO dto = new PostReactionRequestDTO(Reactions.LIKE);
        doNothing().when(postReactionService).reactToPost(postId, Reactions.LIKE);

        mockMvc.perform(post("/api/posts/{postId}/reaction", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(postReactionService, times(1)).reactToPost(postId, Reactions.LIKE);
    }

    @Test
    void reactToPost_postNotFound() throws Exception {
        PostReactionRequestDTO dto = new PostReactionRequestDTO(Reactions.LIKE);
        doThrow(new PostNotFoundException("Пост не найден"))
                .when(postReactionService).reactToPost(postId, Reactions.LIKE);

        mockMvc.perform(post("/api/posts/{postId}/reaction", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    /* ================= DELETE REACTION ================= */

    @Test
    void deleteReaction_success() throws Exception {
        doNothing().when(postReactionService).deleteReaction(postId);

        mockMvc.perform(delete("/api/posts/{postId}/reaction", postId))
                .andExpect(status().isNoContent());

        verify(postReactionService, times(1)).deleteReaction(postId);
    }

    @Test
    void deleteReaction_notFound() throws Exception {
        doThrow(new PostNotFoundException("Реакция не найдена"))
                .when(postReactionService).deleteReaction(postId);

        mockMvc.perform(delete("/api/posts/{postId}/reaction", postId))
                .andExpect(status().isNotFound());
    }
}