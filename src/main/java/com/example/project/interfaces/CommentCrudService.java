package com.example.project.interfaces;

import com.example.project.dto.comment.CommentDTO;
import com.example.project.entity.Comment;
import com.example.project.enums.CommentReactions;

import java.util.List;
import java.util.UUID;

public interface CommentCrudService {
    Comment createComment(CommentDTO commentDTO);
    Comment updateComment(UUID commentId, CommentDTO commentDTO);
    void deleteComment(UUID commentId);
    Comment getComment(UUID commentId);
    List<Comment> getCommentsByPost(UUID postId);
    List<Comment> getCommentsByUser(UUID postId);
    void reactToComment(UUID commentId, UUID userId, CommentReactions reaction);
    void removeReactionFromComment(UUID commentId, UUID userId);
    Comment addCommentToComment(UUID parentCommentId, CommentDTO commentDTO);

}
