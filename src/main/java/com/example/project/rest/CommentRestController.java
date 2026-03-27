package com.example.project.rest;

import com.example.project.dto.comment.CommentDTO;
import com.example.project.entity.Comment;
import com.example.project.enums.CommentReactions;
import com.example.project.interfaces.CommentCrudService;
import com.example.project.mappers.CommentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comments", description = "API для работы с комментариями")
public class CommentRestController {

    private final CommentCrudService commentService;
    private final CommentMapper commentMapper;

    @PostMapping
    @Operation(summary = "Создать комментарий")
    public ResponseEntity<CommentDTO> createComment(@RequestBody CommentDTO commentDTO) {
        log.info("REST: создание комментария");
        Comment comment = commentService.createComment(commentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(commentMapper.toDto(comment));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Обновить комментарий")
    public ResponseEntity<CommentDTO> updateComment(
            @PathVariable UUID commentId,
            @RequestBody CommentDTO commentDTO) {
        log.info("REST: обновление комментария с ID: {}", commentId);
        Comment comment = commentService.updateComment(commentId, commentDTO);
        return ResponseEntity.ok(commentMapper.toDto(comment));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Удалить комментарий")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID commentId) {
        log.info("REST: удаление комментария с ID: {}", commentId);
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Получить комментарий по ID")
    public ResponseEntity<CommentDTO> getComment(@PathVariable UUID commentId) {
        log.info("REST: получение комментария с ID: {}", commentId);
        Comment comment = commentService.getComment(commentId);
        return ResponseEntity.ok(commentMapper.toDto(comment));
    }

    @GetMapping("/post/{postId}")
    @Operation(summary = "Получить все комментарии к посту")
    public ResponseEntity<List<CommentDTO>> getCommentsByPost(@PathVariable UUID postId) {
        log.info("REST: получение комментариев для поста с ID: {}", postId);
        List<Comment> comments = commentService.getCommentsByPost(postId);
        return ResponseEntity.ok(comments.stream().map(commentMapper::toDto).toList());
    }

    @GetMapping("/user/{postId}")
    @Operation(summary = "Получить комментарии пользователя к посту")
    public ResponseEntity<List<CommentDTO>> getCommentsByUser(@PathVariable UUID postId) {
        log.info("REST: получение комментариев пользователя для поста с ID: {}", postId);
        List<Comment> comments = commentService.getCommentsByUser(postId);
        return ResponseEntity.ok(comments.stream().map(commentMapper::toDto).toList());
    }

    @PostMapping("/{commentId}/react")
    @Operation(summary = "Добавить реакцию к комментарию")
    public ResponseEntity<Void> reactToComment(
            @PathVariable UUID commentId,
            @RequestParam UUID userId,
            @RequestParam CommentReactions reaction) {
        log.info("REST: добавление реакции к комментарию с ID: {}", commentId);
        commentService.reactToComment(commentId, userId, reaction);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}/react")
    @Operation(summary = "Удалить реакции с комментария")
    public ResponseEntity<Void> removeReactionFromComment(
            @PathVariable UUID commentId,
            @RequestParam UUID userId) {
        log.info("REST: удаление реакций с комментария с ID: {}", commentId);
        commentService.removeReactionFromComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{parentCommentId}/reply")
    @Operation(summary = "Добавить ответ на комментарий")
    public ResponseEntity<CommentDTO> addCommentToComment(
            @PathVariable UUID parentCommentId,
            @RequestBody CommentDTO commentDTO) {
        log.info("REST: добавление ответа на комментарий с ID: {}", parentCommentId);
        Comment comment = commentService.addCommentToComment(parentCommentId, commentDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(commentMapper.toDto(comment));
    }
}