package com.example.project.interfaces;

import com.example.project.dto.PostDTO;
import com.example.project.entity.Post;

import java.util.UUID;

public interface PostCrudService {

    Post createPost(PostDTO post);

    void updatePost(UUID postId, PostDTO post);

    void deletePost(UUID postId);

    Post getPostById(UUID postId);

}
