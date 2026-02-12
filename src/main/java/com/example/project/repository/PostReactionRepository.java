package com.example.project.repository;

import com.example.project.entity.PostReaction;
import com.example.project.enums.Reactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostReactionRepository extends JpaRepository<PostReaction, UUID> {
    Optional<PostReaction> findByPostIdAndUserId(UUID postId, UUID userId);

    List<PostReaction> findByPostId(UUID postId);

    long countByPostId(UUID postId);

    long countByPostIdAndReaction(UUID postId, Reactions reaction);

    void deleteByPostId(UUID postId);

    void deleteByPostIdAndUserId(UUID postId, UUID userId);
}