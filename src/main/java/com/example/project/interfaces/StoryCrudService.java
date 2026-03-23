package com.example.project.interfaces;

import com.example.project.dto.story.StoryDTO;
import com.example.project.entity.Story;

import java.util.List;
import java.util.UUID;

public interface StoryCrudService {
    Story createStory(StoryDTO story) throws Exception;

    void deleteStory(UUID storyId);

    void viewStory(UUID storyId, UUID viewerId);

    boolean hasViewed(UUID storyId, UUID viewerId);

    long countViews(UUID storyId);


    List<Story> getAllActiveStories();

    List<Story> getStoriesByUserId(UUID userId);

    Story getStoryById(UUID storyId);

    void setAllStoriesPrivacy(UUID userId, boolean isPublic);


}
