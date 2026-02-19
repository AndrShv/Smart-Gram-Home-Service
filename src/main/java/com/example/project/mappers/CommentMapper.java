package com.example.project.mappers;

import com.example.project.dto.CommentDTO;
import com.example.project.dto.CreateCommentRequestDTO;
import com.example.project.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "reactions", ignore = true)
    Comment toEntity(CreateCommentRequestDTO dto, UUID userId);

    @Mapping(target = "reactionsCount", expression = "java(comment.getReactions() != null ? comment.getReactions().size() : 0)")
    CommentDTO toDto(Comment comment);
}