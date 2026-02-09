package com.example.project.rest;

import com.example.project.dto.PostDTO;
import com.example.project.dto.PostReactionRequestDTO;
import com.example.project.entity.Post;
import com.example.project.enums.Reactions;
import com.example.project.interfaces.PostCrudService;
import com.example.project.interfaces.PostReactionService;
import com.example.project.mappers.PostMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/posts")
@AllArgsConstructor
public class PostRestController {

    private final PostCrudService postService;
    private final PostReactionService postReactionService;
    private final PostMapper postMapper; // Добавьте маппер

    /* ================= CREATE POST ================= */
    @PostMapping
    public ResponseEntity<PostDTO> createPost(@RequestBody @Valid PostDTO postDTO) {
        log.info("REST: создание нового поста");
        Post createdPost = postService.createPost(postDTO);
        PostDTO responseDTO = postMapper.toDto(createdPost);
        return ResponseEntity.status(201).body(responseDTO);
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
}