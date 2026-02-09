package com.example.project.interfaces;

import com.example.project.dto.StoryReactionCountDTO;
import com.example.project.entity.StoryReaction;
import com.example.project.enums.Reactions;

import java.util.List;
import java.util.UUID;

public interface PostReactionService {
    void reactToPost(UUID postId, Reactions reaction);

    void deleteReaction(UUID postId);


}
