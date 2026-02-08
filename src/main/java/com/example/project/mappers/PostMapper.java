package com.example.project.mappers;


import com.example.project.dto.*;
import com.example.project.entity.Post;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(
        componentModel = "spring",
        uses = { CommentMapper.class }
)
public interface PostMapper {

    /* ================= CREATE ================= */

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "viewers", ignore = true)
    @Mapping(target = "reactions", ignore = true)
    @Mapping(target = "comments", ignore = true)
    Post toEntity(PostCreateRequestDTO dto, UUID userId);

    /* ================= FEED ================= */

    @Mapping(target = "id", expression = "java(post.getId().toString())")
    @Mapping(target = "viewsCount",
            expression = "java(post.getViewers() == null ? 0 : post.getViewers().size())")
    @Mapping(target = "reactionsCount",
            expression = "java(post.getReactions() == null ? 0 : post.getReactions().size())")
    @Mapping(target = "commentsCount",
            expression = "java(post.getComments() == null ? 0 : post.getComments().size())")
    PostDTO toDto(Post post);

    /* ================= DETAILS ================= */

    @Mapping(target = "id", expression = "java(post.getId().toString())")
    @Mapping(target = "reactionsCount",
            expression = "java(post.getReactions() == null ? 0 : post.getReactions().size())")
    PostDetailsDTO toDetailsDto(Post post);
}

