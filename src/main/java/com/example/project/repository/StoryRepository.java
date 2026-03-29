package com.example.project.repository;

import com.example.project.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface StoryRepository extends JpaRepository<Story, UUID> {
    List<Story> findByExpireAtAfter(LocalDateTime now);
    List<Story> findByUserIdAndExpireAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime now);
    List<Story> findByExpireAtAfterAndIsPublicTrue(LocalDateTime now);


}
