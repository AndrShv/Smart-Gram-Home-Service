package com.example.project.service;

import com.example.project.event.NotificationMessage;
import com.example.project.enums.NotificationType;
import com.example.project.metrics.HomeRabbitMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final TopicExchange notificationExchange;
    private final RabbitTemplate rabbitTemplate;
    private final HomeRabbitMetricsService rabbitMetrics;

    // ── POST ──────────────────────────────────────────────

    public void sendPostCreated(String postId, String actorId, String recipientId, String username) {
        send(NotificationMessage.builder()
                .type(NotificationType.POST_CREATED)
                .entityId(postId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " опубликовал новый пост")
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendPostLiked(String postId, String actorId, String recipientId, String username) {
        send(NotificationMessage.builder()
                .type(NotificationType.POST_LIKED)
                .entityId(postId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " лайкнул ваш пост")
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendPostCommented(String postId, String actorId, String recipientId, String username) {
        send(NotificationMessage.builder()
                .type(NotificationType.POST_COMMENTED)
                .entityId(postId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " прокомментировал ваш пост")
                .createdAt(LocalDateTime.now())
                .build());
    }

    // ── STORY ─────────────────────────────────────────────

    public void sendStoryCreated(String storyId, String actorId, String recipientId, String username) {
        send(NotificationMessage.builder()
                .type(NotificationType.STORY_CREATED)
                .entityId(storyId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " опубликовал новую историю")
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendStoryViewed(String storyId, String actorId, String recipientId, String username) {
        send(NotificationMessage.builder()
                .type(NotificationType.STORY_VIEWED)
                .entityId(storyId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " просмотрел вашу историю")
                .createdAt(LocalDateTime.now())
                .build());
    }
    // ───COMMENT ──────────────────────────────────────────

    public void sendCreateCommentInPostToPostOwner(String commentId, String actorId, String recipientId, String username) {
        if (actorId.equals(recipientId)) return;

        send(NotificationMessage.builder()
                .type(NotificationType.COMMENT_CREATED)
                .entityId(commentId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " прокомментировал ваш пост")
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendCreateCommentInPostToCommentOwner(String commentId, String postId, String actorId, String recipientId, String username) {
        send(NotificationMessage.builder()
                .type(NotificationType.COMMENT_CREATED)
                .entityId(commentId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " ответил на ваш комментарий")
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendCreateCommentReactionToCommentOwner(String commentId, String actorId, String recipientId, String username, CommentReactions reaction) {
        send(NotificationMessage.builder()
                .type(NotificationType.COMMENT_REATED)
                .entityId(commentId)
                .actorId(actorId)
                .recipientId(recipientId)
                .username(username)
                .message(username + " поставил реакцию " + reaction.name() + " на ваш комментарий")
                .createdAt(LocalDateTime.now())
                .build());
    }
    // ── INTERNAL ──────────────────────────────────────────

    private void send(NotificationMessage msg) {
        rabbitTemplate.convertAndSend(
                String.valueOf(notificationExchange),
                "notification." + msg.getType().name().toLowerCase(),
                msg
        );
        rabbitMetrics.increment();
        log.info("Notification sent → type={} entityId={} recipient={}",
                msg.getType(), msg.getEntityId(), msg.getRecipientId());
    }
}