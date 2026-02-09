package com.example.project.mappers;

import com.example.project.dto.*;
import com.example.project.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring", uses = { CommentMapper.class })
public interface PostMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "viewers", ignore = true)
    @Mapping(target = "reactions", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Post toEntity(PostCreateRequestDTO dto, UUID userId);

    @Mapping(target = "viewsCount", expression = "java(post.getViewers() != null ? post.getViewers().size() : 0)")
    @Mapping(target = "reactionsCount", expression = "java(post.getReactions() != null ? post.getReactions().size() : 0)")
    @Mapping(target = "commentsCount", expression = "java(post.getComments() != null ? post.getComments().size() : 0)")
    PostDTO toDto(Post post);

    @Mapping(target = "reactionsCount", expression = "java(post.getReactions() != null ? post.getReactions().size() : 0)")
    PostDetailsDTO toDetailsDto(Post post);
}