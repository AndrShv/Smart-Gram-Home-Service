package com.example.project.mappers;

import com.example.project.dto.comment.CommentDTO;
import com.example.project.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    // =========================
    // DTO -> ENTITY
    // =========================

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "text", source = "dto.text")
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "postId", source = "dto.postId")
    @Mapping(target = "parentComment", ignore = true)
    @Mapping(target = "childComments", ignore = true)
    @Mapping(target = "reactions", expression = "java(dto.getReactions() != null ? dto.getReactions() : new java.util.ArrayList<>())")
    Comment toEntity(CommentDTO dto, UUID userId);

    // =========================
    // ENTITY -> DTO (без replies)
    // =========================

    @Named("toDto")
    @Mapping(target = "parentCommentId", expression = "java(comment.getParentComment() != null ? comment.getParentComment().getId() : null)")
    @Mapping(target = "reactionsCount", expression = "java(comment.getReactions() != null ? comment.getReactions().size() : 0)")
    @Mapping(target = "replies", ignore = true)
    CommentDTO toDto(Comment comment);

    // =========================
    // ENTITY -> DTO (с replies)
    // =========================

    @Named("toDtoWithReplies")
    @Mapping(target = "parentCommentId", expression = "java(comment.getParentComment() != null ? comment.getParentComment().getId() : null)")
    @Mapping(target = "reactionsCount", expression = "java(comment.getReactions() != null ? comment.getReactions().size() : 0)")
    @Mapping(target = "replies", expression = "java(comment.getChildComments() != null ? comment.getChildComments().stream().map(this::toDtoWithReplies).toList() : new java.util.ArrayList<>())")
    CommentDTO toDtoWithReplies(Comment comment);

    // =========================
    // ONLY ONE LIST METHOD FOR MAPSTRUCT
    // =========================

    List<CommentDTO> toDtoList(List<Comment> comments);

    // =========================
    // CUSTOM METHOD FOR TREE
    // =========================

    default List<CommentDTO> toDtoListWithReplies(List<Comment> comments) {
        if (comments == null) {
            return new ArrayList<>();
        }

        return comments.stream()
                .map(this::toDtoWithReplies)
                .toList();
    }

    Object tp(Comment comment);
}