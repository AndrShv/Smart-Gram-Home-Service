package com.example.project.repository;

import com.example.project.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    boolean existsByIdAndUserId(UUID commentId, UUID userId);
    Optional<List<Comment>> findAllCommentsByPostId(UUID postId);
    Optional <List<Comment>> findAllCommentsByPostIdAndUserId(UUID postId, UUID uuid);
    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(UUID postId);

}
