package com.example.project.interfaces;

import com.example.project.dto.PostReactionCountDTO;
import com.example.project.entity.PostReaction;
import com.example.project.enums.Reactions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostReactionService {

    void reactToPost(UUID postId, Reactions reaction);

    void deleteReaction(UUID postId);

    long countReactions(UUID postId);

    List<PostReaction> getReactionsByPostId(UUID postId);

    List<PostReactionCountDTO> getReactionStats(UUID postId);

    boolean hasUserReacted(UUID postId, UUID userId);

    Optional<PostReaction> getUserReaction(UUID postId, UUID userId);

    long countReactionsByType(UUID postId, Reactions reaction);
}