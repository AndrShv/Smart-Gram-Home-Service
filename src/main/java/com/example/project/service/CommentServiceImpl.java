package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.PostClient;
import com.example.project.dto.comment.CommentDTO;
import com.example.project.dto.post.PostDTO;
import com.example.project.dto.user.UserResponseDTO;
import com.example.project.entity.Comment;
import com.example.project.enums.CommentReactions;
import com.example.project.exceptions.*;
import com.example.project.interfaces.CommentCrudService;
import com.example.project.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentCrudService {

    private final AuthClient authClient;
    private final PostClient postClient;
    private final CommentRepository commentRepository;
    private final NotificationProducer notificationProducer;

    @Override
    @Transactional
    public Comment createComment(CommentDTO commentDTO) {
        log.info("Создание нового комментария: {}", commentDTO);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        validateCommentText(commentDTO.getText());

        UUID existingPostId = validateAndGetPostId(commentDTO.getPostId());

        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .userId(currentUserId)
                .text(commentDTO.getText().trim())
                .createdAt(LocalDateTime.now())
                .postId(existingPostId)
                .parentComment(null)
                .build();

        commentRepository.save(comment);
        log.info("Комментарий успешно сохранён с ID: {}", comment.getId());

        notificationProducer.sendCreateCommentInPostToPostOwner(
                existingPostId.toString(),
                currentUserId.toString(),
                postClient.getPost(existingPostId).getUserId().toString(),
                currentUser.getUsername()
        );
        log.info("Уведомление о новом комментарии успешно отправлено владельцу поста с ID: {}", existingPostId);
        return comment;
    }

    @Override
    @Transactional
    public Comment updateComment(UUID commentId, CommentDTO commentDTO) {
        log.info("Обновление комментария с ID: {}. Новые данные: {}", commentId, commentDTO);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());
        boolean isAdmin = isAdmin(currentUser);

        if (!isAdmin && !commentRepository.existsByIdAndUserId(commentId, currentUserId)) {
            log.warn("Пользователь с ID: {} не имеет прав на обновление комментария с ID: {}", currentUserId, commentId);
            throw new UnauthorizedException("У вас нет прав на обновление этого комментария");
        }

        Comment existingComment = commentRepository.findById(commentId)
                .orElseThrow(() -> {
                    log.warn("Комментарий с ID: {} не найден для обновления", commentId);
                    return new CommentNotFoundException("Комментарий с таким ID не найден");
                });

        validateCommentText(commentDTO.getText());

        existingComment.setText(commentDTO.getText().trim());

        commentRepository.save(existingComment);
        log.info("Комментарий с ID: {} успешно обновлён", commentId);

        return existingComment;
    }

    @Override
    @Transactional
    public void deleteComment(UUID commentId) {
        log.info("Удаление комментария с ID: {}", commentId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());
        boolean isAdmin = isAdmin(currentUser);

        if (!isAdmin && !commentRepository.existsByIdAndUserId(commentId, currentUserId)) {
            log.warn("Пользователь с ID: {} не имеет прав на удаление комментария с ID: {}", currentUserId, commentId);
            throw new UnauthorizedException("У вас нет прав на удаление этого комментария");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> {
                    log.warn("Комментарий с ID: {} не найден для удаления", commentId);
                    return new CommentNotFoundException("Комментарий с таким ID не найден");
                });

        commentRepository.delete(comment);
        log.info("Комментарий с ID: {} успешно удалён", commentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Comment getComment(UUID commentId) {
        log.info("Получение комментария с ID: {}", commentId);

        return commentRepository.findById(commentId)
                .orElseThrow(() -> {
                    log.warn("Комментарий с ID: {} не найден для просмотра", commentId);
                    return new CommentNotFoundException("Комментарий с таким ID не найден");
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> getCommentsByPost(UUID postId) {
        log.info("Получение комментариев для поста с ID: {}", postId);

        validateAndGetPostId(postId);

        List<Comment> existedComments = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(postId);

        if (existedComments.isEmpty()) {
            log.warn("Комментарии для поста с ID: {} не найдены", postId);
            throw new CommentByPostNotFoundException("Комментарии для данного поста не найдены");
        }

        log.info("Комментарии для поста с ID: {} успешно получены", postId);
        return existedComments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> getCommentsByUser(UUID postId) {
        UserResponseDTO currentUser = getAuthenticatedUser();
        boolean isAdmin = isAdmin(currentUser);

        PostDTO postDTO = postClient.getPost(postId);
        if (postDTO == null) {
            log.warn("Пост с ID: {} не найден для получения комментариев", postId);
            throw new PostNotFoundException("Пост с таким ID не найден");
        }

        List<Comment> comments;

        if (isAdmin) {
            comments = commentRepository.findAllCommentsByPostId(postId)
                    .orElseThrow(() -> {
                        log.warn("Комментарии для поста с ID: {} не найдены", postId);
                        return new CommentByPostNotFoundException("Комментарии для данного поста не найдены");
                    });
        } else {
            comments = commentRepository.findAllCommentsByPostIdAndUserId(postId, UUID.fromString(currentUser.getId()))
                    .orElseThrow(() -> {
                        log.warn("Комментарии для поста с ID: {} от пользователя с ID: {} не найдены", postId, currentUser.getId());
                        return new CommentByPostNotFoundException("Комментарии для данного поста от текущего пользователя не найдены");
                    });
        }

        log.info("Комментарии пользователя успешно получены для поста с ID: {}", postId);
        return comments;
    }

    @Override
    @Transactional
    public void reactToComment(UUID commentId, UUID userId, CommentReactions addedReaction) {
        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> {
                    log.warn("Комментарий с ID: {} не найден для реакции", commentId);
                    return new CommentNotFoundException("Комментарий с таким ID не найден");
                });

        List<CommentReactions> reactions = comment.getReactions();
        if (reactions == null) {
            reactions = new ArrayList<>();
        }

        reactions.add(addedReaction);

        log.info("Пользователь с ID: {} добавляет реакцию {} к комментарию с ID: {}",
                currentUserId, addedReaction, commentId);

        comment.setReactions(reactions);
        commentRepository.save(comment);

        log.info("Реакция {} успешно добавлена к комментарию с ID: {} пользователем с ID: {}",
                addedReaction, commentId, currentUserId);


        notificationProducer.sendCreateCommentReactionToCommentOwner(
                commentId.toString(),
                currentUserId.toString(),
                comment.getUserId().toString(),
                currentUser.getUsername(),
                addedReaction
        );
        log.info("Уведомление о новой реакции на комментарий с ID: {} успешно); отправлено владельцу комментария с ID: {}", commentId, comment.getUserId());
    }

    @Override
    @Transactional
    public void removeReactionFromComment(UUID commentId, UUID userId) {
        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> {
                    log.warn("Комментарий с ID: {} не найден для удаления реакции", commentId);
                    return new CommentNotFoundException("Комментарий с таким ID не найден");
                });

        log.info("Пользователь с ID: {} удаляет все реакции у комментария с ID: {}", currentUserId, commentId);

        comment.setReactions(new ArrayList<>());
        commentRepository.save(comment);

        log.info("Реакции успешно удалены у комментария с ID: {} пользователем с ID: {}", commentId, currentUserId);
    }

    @Override
    @Transactional
    public Comment addCommentToComment(UUID parentCommentId, CommentDTO commentDTO) {
        log.info("Добавление reply к комментарию с ID: {}", parentCommentId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        validateCommentText(commentDTO.getText());

        UUID existingPostId = validateAndGetPostId(commentDTO.getPostId());

        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> {
                    log.warn("Родительский комментарий с ID: {} не найден", parentCommentId);
                    return new CommentNotFoundException("Родительский комментарий с таким ID не найден");
                });

        if (!parentComment.getPostId().equals(existingPostId)) {
            log.warn("Попытка добавить reply к комментарию из другого поста. parentCommentId={}, postId={}",
                    parentCommentId, existingPostId);
            throw new IllegalArgumentException("Нельзя добавить reply к комментарию из другого поста");
        }

        Comment comment = Comment.builder()
                .id(UUID.randomUUID())
                .userId(currentUserId)
                .text(commentDTO.getText().trim())
                .createdAt(LocalDateTime.now())
                .postId(existingPostId)
                .parentComment(parentComment)
                .build();

        Comment savedComment = commentRepository.save(comment);

        log.info("Комментарий-ответ успешно добавлен. ID reply: {}, parent ID: {}", savedComment.getId(), parentCommentId);


        notificationProducer.sendCreateCommentInPostToCommentOwner(
                savedComment.getId().toString(),
                existingPostId.toString(),
                currentUserId.toString(),
                parentComment.getUserId().toString(),
                currentUser.getUsername()
        );
        log.info("Уведомление о новом комментарии-ответе успешно отправлено владельцу родительского комментария с ID: {}", parentComment.getUserId());


        return savedComment;
    }


    // =========================
    // HELPERS
    // =========================

    private UUID validateAndGetPostId(UUID postId) {
        PostDTO postDTO = postClient.getPost(postId);
        if (postDTO == null || postDTO.getId() == null) {
            log.warn("Пост с ID: {} не найден", postId);
            throw new PostNotFoundException("Пост с таким ID не существует");
        }
        return postDTO.getId();
    }

    private void validateCommentText(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Попытка создать/обновить комментарий с пустым текстом");
            throw new UnavailableTextForCommentException("Текст комментария не может быть пустым");
        }
    }

    private boolean isAdmin(UserResponseDTO currentUser) {
        return currentUser.getRole() != null && currentUser.getRole().equalsIgnoreCase("ADMIN");
    }

    UserResponseDTO getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("JWT истёк");
        }

        UserResponseDTO currentUser = authClient.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            throw new UnauthorizedException("Пользователь не найден");
        }

        return currentUser;
    }
}