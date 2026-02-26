package com.example.project.interfaces;

import com.example.project.dto.count.StoryReactionCountDTO;
import com.example.project.enums.Reactions;

import java.util.List;
import java.util.UUID;

public interface StoryReactionService {
    void reactToStory(UUID storyId, Reactions reaction);

    void deleteReaction(UUID storyId);

    List<StoryReactionCountDTO> getReactionStats(UUID storyId);


}
