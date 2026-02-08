package com.example.project.mappers;


import com.example.project.dto.CommentDTO;
import com.example.project.dto.CreateCommentRequestDTO;
import com.example.project.entity.Comment;
import org.mapstruct.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    /* ================= CREATE ================= */

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "reactions", ignore = true)
    Comment toEntity(CreateCommentRequestDTO dto, UUID userId);

    /* ================= READ ================= */

    @Mapping(target = "id", expression = "java(comment.getId().toString())")
    @Mapping(target = "userId", expression = "java(comment.getUserId().toString())")
    @Mapping(target = "reactionsCount",
            expression = "java(comment.getReactions() == null ? 0 : comment.getReactions().size())")
    CommentDTO toDto(Comment comment);
}

