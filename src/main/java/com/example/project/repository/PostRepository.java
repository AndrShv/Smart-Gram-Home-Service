package com.example.project.repository;


import com.example.project.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;


import com.example.project.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    List<Post> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserId(UUID userId);


    List<Post> findByUserId(UUID userId);
}