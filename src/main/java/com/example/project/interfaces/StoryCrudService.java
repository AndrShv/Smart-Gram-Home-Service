package com.example.project.interfaces;

import com.example.project.dto.StoryDTO;
import com.example.project.entity.Story;

import java.util.UUID;

public interface StoryCrudService {
    Story createStory(StoryDTO story);

    void deleteStory(UUID storyId);

    void viewStory(UUID storyId, UUID viewerId);

    boolean hasViewed(UUID storyId, UUID viewerId);

    long countViews(UUID storyId);
}
