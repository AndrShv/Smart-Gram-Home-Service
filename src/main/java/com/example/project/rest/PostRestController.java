package com.example.project.rest;

import com.example.project.dto.PostCreateRequestDTO;
import com.example.project.dto.PostDTO;
import com.example.project.dto.PostReactionRequestDTO;
import com.example.project.entity.Post;
import com.example.project.interfaces.PostCrudService;
import com.example.project.interfaces.PostReactionService;
import com.example.project.mappers.PostMapper;
import com.example.project.service.MinioService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/posts")
@AllArgsConstructor
public class PostRestController {

    private final PostCrudService postService;
    private final PostReactionService postReactionService;
    private final PostMapper postMapper;
    private final MinioService minioService;

    /* ================= GET FEED (лента постов) ================= */
    @GetMapping("/feed")
    public ResponseEntity<List<PostDTO>> getFeed() {
        log.info("REST: получение ленты постов");
        UUID userId = getCurrentUserId();

        List<Post> posts = postService.getAllPosts();

        List<PostDTO> postDTOs = posts.stream()
                .map(postMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(postDTOs);
    }

    /* ================= CREATE POST ================= */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostDTO> createPost(
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "isPublic", defaultValue = "true") boolean isPublic,
            @RequestPart("photo") MultipartFile photo
    ) {
        log.info("REST: создание нового поста");

        UUID userId = getCurrentUserId();

        byte[] photoBytes;
        try {
            photoBytes = photo.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось прочитать файл", e);
        }

        String photoUrl = minioService.uploadPostPhotoBytes(
                photoBytes,
                photo.getOriginalFilename(),
                photo.getContentType()
        );

        PostDTO postDTO = new PostDTO();
        postDTO.setDescription(description);
        postDTO.setPhotoUrl(photoUrl);
        postDTO.setUserId(userId);
        postDTO.setLocation(location);
        postDTO.setPublic(isPublic);
        postDTO.setPhotoBytes(photoBytes);
        postDTO.setPhotoFileName(photo.getOriginalFilename());


        Post createdPost = postService.createPost(postDTO);
        PostDTO responseDTO = postMapper.toDto(createdPost);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    /* ================= GET POST BY ID ================= */
    @GetMapping("/{postId}")
    public ResponseEntity<PostDTO> getPost(@PathVariable UUID postId) {
        log.info("REST: получение поста с ID {}", postId);
        Post post = postService.getPostById(postId);
        PostDTO responseDTO = postMapper.toDto(post);
        return ResponseEntity.ok(responseDTO);
    }

    /* ================= UPDATE POST ================= */
    @PutMapping("/{postId}")
    public ResponseEntity<Void> updatePost(@PathVariable UUID postId, @RequestBody @Valid PostDTO postDTO) {
        log.info("REST: обновление поста с ID {}", postId);
        postService.updatePost(postId, postDTO);
        return ResponseEntity.ok().build();
    }

    /* ================= DELETE POST ================= */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID postId) {
        log.info("REST: удаление поста с ID {}", postId);
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    /* ================= REACT TO POST ================= */
    @PostMapping("/{postId}/reaction")
    public ResponseEntity<Void> reactToPost(
            @PathVariable UUID postId,
            @RequestBody @Valid PostReactionRequestDTO dto
    ) {
        log.info("REST: реакция {} на пост {}", dto.getReaction(), postId);
        postReactionService.reactToPost(postId, dto.getReaction());
        return ResponseEntity.ok().build();
    }

    /* ================= DELETE REACTION ================= */
    @DeleteMapping("/{postId}/reaction")
    public ResponseEntity<Void> deleteReaction(@PathVariable UUID postId) {
        log.info("REST: удаление реакции с поста {}", postId);
        postReactionService.deleteReaction(postId);
        return ResponseEntity.noContent().build();
    }

    /* ================= GET REACTIONS COUNT ================= */
    @GetMapping("/{postId}/reactions/count")
    public ResponseEntity<Long> getReactionsCount(@PathVariable UUID postId) {
        log.info("REST: получение количества реакций для поста {}", postId);

        long count = postReactionService.countReactions(postId);

        return ResponseEntity.ok(count);
    }

    /* ================= GET USER POSTS ================= */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDTO>> getUserPosts(@PathVariable UUID userId) {
        log.info("REST: получение постов пользователя {}", userId);

        List<Post> posts = postService.getPostsByUserId(userId);

        List<PostDTO> postDTOs = posts.stream()
                .map(postMapper::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(postDTOs);
    }



    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Пользователь не авторизован");
        }
        return UUID.fromString(authentication.getName());
    }


}