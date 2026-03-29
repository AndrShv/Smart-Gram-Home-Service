package com.example.project.clients;

import com.example.project.configs.FeignClientInterceptor;
import com.example.project.dto.CommentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "comment-service", url = "${COMMENT_SERVICE_URL}", configuration = FeignClientInterceptor.class)
public interface CommentClient {

    @GetMapping("/api/comments/{commentId}")
    CommentDTO getComment(@PathVariable("commentId") UUID commentId);

    @GetMapping("/api/comments/post/{postId}")
    List<CommentDTO> getCommentsByPost(@PathVariable("postId") UUID postId);

    @PostMapping("/api/comments")
    CommentDTO createComment(@RequestBody CommentDTO commentDTO);

    @PutMapping("/api/comments/{commentId}")
    CommentDTO updateComment(@PathVariable("commentId") UUID commentId,
                             @RequestBody CommentDTO commentDTO);

    @DeleteMapping("/api/comments/{commentId}")
    void deleteComment(@PathVariable("commentId") UUID commentId);

    @PostMapping("/api/comments/{commentId}/react")
    void reactToComment(@PathVariable("commentId") UUID commentId,
                        @RequestParam("userId") UUID userId,
                        @RequestParam("reaction") String reaction);

    @DeleteMapping("/api/comments/{commentId}/react")
    void removeReaction(@PathVariable("commentId") UUID commentId,
                        @RequestParam("userId") UUID userId);

    @PostMapping("/api/comments/{parentCommentId}/reply")
    CommentDTO replyToComment(@PathVariable("parentCommentId") UUID parentCommentId,
                              @RequestBody CommentDTO commentDTO);
}