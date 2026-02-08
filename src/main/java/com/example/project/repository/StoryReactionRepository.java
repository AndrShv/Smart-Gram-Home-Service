package com.example.project.repository;

import com.example.project.entity.StoryReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoryReactionRepository extends JpaRepository<StoryReaction, UUID> {
    @Query("""
            SELECT r.reaction, COUNT(r)
            FROM StoryReaction r
            WHERE r.story.id = :storyId
            GROUP BY r.reaction
            """)
    List<Object[]> countReactionsByStory(UUID storyId);


    Optional<StoryReaction> findByStoryIdAndUserId(UUID storyId, UUID userId);
}
