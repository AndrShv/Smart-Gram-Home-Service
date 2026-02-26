package com.example.project.interfaces;

import com.example.project.dto.post.PostDTO;
import com.example.project.entity.Post;

import java.util.List;
import java.util.UUID;

public interface PostCrudService {

    Post createPost(PostDTO post);

    void updatePost(UUID postId, PostDTO post);

    void deletePost(UUID postId);

    Post getPostById(UUID postId);

    List<Post> getAllPosts();

    List<Post> getPostsByUserId(UUID userId);

    List<Post> getFeedForUser(UUID userId);

    long countPostsByUserId(UUID userId);

    boolean isPostOwner(UUID postId, UUID userId);

    void setAllPostsPrivacy(UUID userId, boolean isPublic);

    List<Post> searchByTag(String tag);

    List<Post> searchPosts(String query);






}