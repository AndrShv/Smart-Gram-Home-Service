package com.example.project.repository;

import com.example.project.entity.StoryViewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;


@Repository
public interface StoryViewerRepository extends JpaRepository<StoryViewer, UUID> {
    long countByStoryId(UUID storyId);

    boolean existsByStoryIdAndViewerId(UUID storyId, UUID viewerId);
}
