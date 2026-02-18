package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.dto.PostDTO;
import com.example.project.dto.PostReactionCountDTO;
import com.example.project.dto.UserResponseDTO;
import com.example.project.entity.Post;
import com.example.project.entity.PostReaction;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.PostNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.PostCrudService;
import com.example.project.interfaces.PostReactionService;
import com.example.project.repository.PostReactionRepository;
import com.example.project.repository.PostRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Builder
@AllArgsConstructor
public class PostServiceImpl implements PostCrudService, PostReactionService {

    private final AuthClient authClient;
    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;

    // ============================================
    //           CRUD ОПЕРАЦИИ
    // ============================================

    @Override
    @Transactional
    public Post createPost(PostDTO post) {
        log.info("Создание нового поста");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("JWT истёк");
        }

        log.info("Получение текущего пользователя из AuthClient");
        UserResponseDTO currentUser = authClient.getCurrentUser();
        UUID userId = UUID.fromString(currentUser.getId());

        log.info("Получен пользователь с ID: {}", userId);

        Post postToSave = Post.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .description(post.getDescription())
                .photoUrl(post.getPhotoUrl())
                .createdAt(LocalDateTime.now())
                .reactions(new ArrayList<>())
                .comments(new ArrayList<>())
                .build();

        log.info("Попытка сохранения поста в репозиторий с ID пользователя: {}", userId);

        postRepository.save(postToSave);
        log.info("Пост успешно сохранён с ID: {}", postToSave.getId());
        return postToSave;
    }

    @Override
    @Transactional
    public void updatePost(UUID postId, PostDTO post) {
        log.info("Обновление поста с ID: {}", postId);
        Post existingPost = postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("❌ Пост с ID {} не найден для обновления", postId);
                    return new PostNotFoundException("Пост с ID " + postId + " не найден");
                });

        existingPost.setDescription(post.getDescription());
        existingPost.setPhotoUrl(post.getPhotoUrl());

        log.info("Попытка сохранения обновленного поста с ID: {}", postId);
        postRepository.save(existingPost);

        log.info("Пост с ID: {} успешно обновлён", postId);
    }

    @Override
    @Transactional
    public void deletePost(UUID postId) {
        log.info("Удаление поста с ID: {}", postId);
        Post existingPost = postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("❌ Пост с ID {} не найден для удаления", postId);
                    return new PostNotFoundException("Пост с ID " + postId + " не найден");
                });

        postRepository.delete(existingPost);
        log.info("Пост с ID: {} успешно удалён", postId);
    }

    @Override
    public Post getPostById(UUID postId) {
        log.info("Получение поста с ID: {}", postId);
        return postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("❌ Пост с ID {} не найден", postId);
                    return new PostNotFoundException("Пост с ID " + postId + " не найден");
                });
    }

    @Override
    public List<Post> getAllPosts() {
        log.info("Получение всех постов");
        return postRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    public List<Post> getPostsByUserId(UUID userId) {
        log.info("Получение постов пользователя с ID: {}", userId);
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<Post> getFeedForUser(UUID userId) {
        log.info("Получение ленты для пользователя с ID: {}", userId);

        return getAllPosts();
    }

    @Override
    public long countPostsByUserId(UUID userId) {
        log.info("Подсчёт постов пользователя с ID: {}", userId);
        return postRepository.countByUserId(userId);
    }

    @Override
    public boolean isPostOwner(UUID postId, UUID userId) {
        log.info("Проверка, принадлежит ли пост {} пользователю {}", postId, userId);
        Post post = getPostById(postId);
        return post.getUserId().equals(userId);
    }

    // ============================================
    //           РЕАКЦИИ
    // ============================================

    @Override
    @Transactional
    public void reactToPost(UUID postId, Reactions reaction) {
        log.info("Попытка реакции {} на пост {}", reaction, postId);

        UUID userId = UUID.fromString(authClient.getCurrentUser().getId());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Пост не найден"));

        Optional<PostReaction> existing = postReactionRepository.findByPostIdAndUserId(postId, userId);

        if (existing.isPresent()) {
            log.info("Обновление реакции пользователя {} на пост {}", userId, postId);
            PostReaction reactionEntity = existing.get();
            reactionEntity.setReaction(reaction);
            reactionEntity.setReactedAt(LocalDateTime.now());

            return;
        }

        log.info("Создание новой реакции пользователя {} на пост {}", userId, postId);

        PostReaction newReaction = PostReaction.builder()
                .post(post)
                .userId(userId)
                .reaction(reaction)
                .reactedAt(LocalDateTime.now())
                .build();

        postReactionRepository.save(newReaction);
    }

    @Override
    @Transactional
    public void deleteReaction(UUID postId) {
        log.info("Попытка удаления реакции пользователя с поста {}", postId);
        UUID userId = UUID.fromString(authClient.getCurrentUser().getId());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Пост не найден"));

        log.info("Пост с ID {} найден для удаления реакции", postId);

        PostReaction reaction = postReactionRepository
                .findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new PostNotFoundException("Реакция не найдена"));

        log.info("Реакция пользователя {} на пост {} найдена для удаления", userId, postId);
        log.info("Удаление реакции пользователя {} с поста {}", userId, postId);

        postReactionRepository.delete(reaction);
    }

    @Override
    public long countReactions(UUID postId) {
        log.info("Подсчёт реакций на пост {}", postId);
        return postReactionRepository.countByPostId(postId);
    }

    @Override
    public List<PostReaction> getReactionsByPostId(UUID postId) {
        log.info("Получение всех реакций на пост {}", postId);
        return postReactionRepository.findByPostId(postId);
    }

    @Override
    public List<PostReactionCountDTO> getReactionStats(UUID postId) {
        log.info("Получение статистики реакций для поста {}", postId);

        List<PostReaction> reactions = postReactionRepository.findByPostId(postId);

        // Группировка по типу реакции и подсчёт
        Map<Reactions, Long> stats = reactions.stream()
                .collect(Collectors.groupingBy(
                        PostReaction::getReaction,
                        Collectors.counting()
                ));

        // Преобразование в DTO
        return stats.entrySet().stream()
                .map(entry -> PostReactionCountDTO.builder()
                        .reaction(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount())) // Сортировка по убыванию
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasUserReacted(UUID postId, UUID userId) {
        log.info("Проверка, поставил ли пользователь {} реакцию на пост {}", userId, postId);
        return postReactionRepository.findByPostIdAndUserId(postId, userId).isPresent();
    }

    @Override
    public Optional<PostReaction> getUserReaction(UUID postId, UUID userId) {
        log.info("Получение реакции пользователя {} на пост {}", userId, postId);
        return postReactionRepository.findByPostIdAndUserId(postId, userId);
    }

    @Override
    public long countReactionsByType(UUID postId, Reactions reaction) {
        log.info("Подсчёт реакций типа {} на пост {}", reaction, postId);
        return postReactionRepository.countByPostIdAndReaction(postId, reaction);
    }
}