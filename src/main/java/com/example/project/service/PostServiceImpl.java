package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.PostReactionCountDTO;
import com.example.project.dto.PostDTO;
import com.example.project.dto.UserResponseDTO;
import com.example.project.entity.Post;
import com.example.project.entity.PostReaction;
import com.example.project.enums.PostCategory;
import com.example.project.enums.PostMood;
import com.example.project.enums.Reactions;
import com.example.project.exceptions.PostNotFoundException;
import com.example.project.exceptions.UnauthorizedException;
import com.example.project.interfaces.AiImageService;
import com.example.project.interfaces.ImaggaService;
import com.example.project.interfaces.PostCrudService;
import com.example.project.interfaces.PostReactionService;
import com.example.project.metrics.HomeRabbitMetricsService;
import com.example.project.metrics.PostMetricsService;
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
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Service
@Builder
@AllArgsConstructor
public class PostServiceImpl implements PostCrudService, PostReactionService {

    private final AuthClient authClient;
    private final SubscriptionClient subscriptionClient;
    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;
    private final ImaggaService imaggaService;
    private final AiImageService aiImageService;
    private final NotificationProducer notificationProducer;
    private final PostMetricsService postMetrics;
    private final HomeRabbitMetricsService rabbitMetrics;
    private final ExecutorService virtualThreadExecutor;
    private final PostAsyncService postAsyncService;

    // ============================================
    //           CRUD ОПЕРАЦИИ
    // ============================================

    @Override
    @Transactional
    public Post createPost(PostDTO post) throws Exception {
        log.info("Создание нового поста");

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        String username = currentUser.getUsername() != null && !currentUser.getUsername().isBlank()
                ? currentUser.getUsername()
                : currentUserId.toString();

        Post postToSave = Post.builder()
                .id(UUID.randomUUID())
                .userId(currentUserId)
                .description(post.getDescription())
                .photoUrl(post.getPhotoUrl())
                .createdAt(LocalDateTime.now())
                .tags(new ArrayList<>())
                .dominantColors(new ArrayList<>())
                .category(PostCategory.OTHER)
                .mood(PostMood.NEUTRAL)
                .location(post.getLocation())
                .isPublic(post.isPublic())
                .reactions(new ArrayList<>())
                .commentIds(new ArrayList<>())
                .build();

        Post savedPost = postRepository.save(postToSave);

        postAsyncService.createPostAsync(savedPost.getId(), post, currentUserId);
        postAsyncService.sendNotificationsAsync(savedPost, currentUserId, username);

        log.info("Пост с ID: {} успешно создан", savedPost.getId());
        return savedPost;
    }

    @Override
    @Transactional
    public void updatePost(UUID postId, PostDTO post) {
        log.info("Обновление поста с ID: {}", postId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());
        boolean isAdmin = currentUser.getRole() != null && currentUser.getRole().equalsIgnoreCase("ADMIN");

        Post existingPost = postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("❌ Пост с ID {} не найден для обновления", postId);
                    return new PostNotFoundException("Пост с ID " + postId + " не найден");
                });

        if (!existingPost.getUserId().equals(currentUserId) && !isAdmin) {
            throw new UnauthorizedException("Вы не можете изменять чужой пост");
        }

        if (isAdmin) {
            log.info("Пользователь {} имеет роль ADMIN, разрешено обновление поста {}", currentUserId, postId);
        }

        existingPost.setDescription(post.getDescription());
        existingPost.setPhotoUrl(post.getPhotoUrl());

        postRepository.save(existingPost);
        postMetrics.incrementUpdated();

        log.info("Пост с ID: {} успешно обновлён", postId);
    }

    @Override
    @Transactional
    public void deletePost(UUID postId) {
        log.info("Удаление поста с ID: {}", postId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        Post existingPost = postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("❌ Пост с ID {} не найден для удаления", postId);
                    return new PostNotFoundException("Пост с ID " + postId + " не найден");
                });

        boolean isAdmin = currentUser.getRole() != null && currentUser.getRole().equalsIgnoreCase("ADMIN");


        if (!existingPost.getUserId().equals(currentUserId) && !isAdmin) {
            throw new UnauthorizedException("Вы не можете удалять чужой пост");
        }
        if (isAdmin) {
            log.info("Пользователь {} имеет роль ADMIN, разрешено удаление поста {}", currentUserId, postId);
        }

        postRepository.delete(existingPost);
        postMetrics.incrementDeleted();

        log.info("Пост с ID: {} успешно удалён", postId);
    }

    @Override
    public Post getPostById(UUID postId) throws Exception {
        log.info("Получение поста с ID: {}", postId);
        return postMetrics.getPostTimer().recordCallable(() ->
                postRepository.findById(postId)
                        .orElseThrow(() -> {
                            log.warn("❌ Пост с ID {} не найден", postId);
                            return new PostNotFoundException("Пост с ID " + postId + " не найден");
                        })
        );
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
    public boolean isPostOwner(UUID postId, UUID userId) throws Exception {
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

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID userId = UUID.fromString(currentUser.getId());

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Пост не найден"));

        Optional<PostReaction> existing = postReactionRepository.findByPostIdAndUserId(postId, userId);

        if (existing.isPresent()) {
            log.info("Обновление реакции пользователя {} на пост {}", userId, postId);
            existing.get().setReaction(reaction);
            existing.get().setReactedAt(LocalDateTime.now());
            postMetrics.incrementReactionAdded();
            return;
        }

        PostReaction newReaction = PostReaction.builder()
                .post(post)
                .userId(userId)
                .reaction(reaction)
                .reactedAt(LocalDateTime.now())
                .build();

        postReactionRepository.save(newReaction);
        postMetrics.incrementReactionAdded();

        try {
            if (!post.getUserId().equals(userId)) {
                notificationProducer.sendPostLiked(
                        postId.toString(),
                        userId.toString(),
                        post.getUserId().toString(),
                        currentUser.getUsername() != null && !currentUser.getUsername().isBlank()
                                ? currentUser.getUsername()
                                : userId.toString()
                );
                rabbitMetrics.increment();
            }
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомление о реакции: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void deleteReaction(UUID postId) {
        log.info("Попытка удаления реакции пользователя с поста {}", postId);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID userId = UUID.fromString(currentUser.getId());

        postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Пост не найден"));

        PostReaction reaction = postReactionRepository
                .findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new PostNotFoundException("Реакция не найдена"));

        postReactionRepository.delete(reaction);
        postMetrics.incrementReactionDeleted();
    }

    @Override
    public long countReactions(UUID postId) {
        return postReactionRepository.countByPostId(postId);
    }

    @Override
    public List<PostReaction> getReactionsByPostId(UUID postId) {
        return postReactionRepository.findByPostId(postId);
    }

    @Override
    public List<PostReactionCountDTO> getReactionStats(UUID postId) {
        List<PostReaction> reactions = postReactionRepository.findByPostId(postId);

        Map<Reactions, Long> stats = reactions.stream()
                .collect(Collectors.groupingBy(PostReaction::getReaction, Collectors.counting()));

        return stats.entrySet().stream()
                .map(e -> PostReactionCountDTO.builder()
                        .reaction(e.getKey())
                        .count(e.getValue())
                        .build())
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasUserReacted(UUID postId, UUID userId) {
        return postReactionRepository.findByPostIdAndUserId(postId, userId).isPresent();
    }

    @Override
    public Optional<PostReaction> getUserReaction(UUID postId, UUID userId) {
        return postReactionRepository.findByPostIdAndUserId(postId, userId);
    }

    @Override
    public long countReactionsByType(UUID postId, Reactions reaction) {
        return postReactionRepository.countByPostIdAndReaction(postId, reaction);
    }

    @Override
    @Transactional
    public void setAllPostsPrivacy(UUID userId, boolean isPublic) {
        log.info("Установка приватности постов пользователя {}: isPublic={}", userId, isPublic);

        UserResponseDTO currentUser = getAuthenticatedUser();
        UUID currentUserId = UUID.fromString(currentUser.getId());

        if (!currentUserId.equals(userId)) {
            throw new UnauthorizedException("Вы не можете менять приватность чужих постов");
        }

        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        posts.forEach(p -> p.setPublic(isPublic));
        postRepository.saveAll(posts);
    }

    public List<Post> searchPosts(String query) {
        String lower = query.toLowerCase();
        return postRepository.findAll().stream()
                .filter(p -> p.isPublic()
                        && p.getDescription() != null
                        && p.getDescription().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public List<Post> searchByTag(String tag) {
        String lower = tag.toLowerCase();
        return postRepository.findAll().stream()
                .filter(p -> p.isPublic()
                        && p.getTags() != null
                        && p.getTags().stream().anyMatch(t -> t.toLowerCase().contains(lower)))
                .collect(Collectors.toList());
    }

    // ============================================
    //           HELPERS
    // ============================================

    private PostCategory mapTagsToCategory(List<String> tags) {
        if (tags == null) return PostCategory.OTHER;
        if (tags.stream().anyMatch(t -> t.contains("food") || t.contains("еда"))) return PostCategory.FOOD;
        if (tags.stream().anyMatch(t -> t.contains("nature") || t.contains("природа"))) return PostCategory.NATURE;
        if (tags.stream().anyMatch(t -> t.contains("travel") || t.contains("путешествие"))) return PostCategory.TRAVEL;
        return PostCategory.OTHER;
    }

    private PostMood mapColorsToMood(List<String> colors) {
        if (colors == null || colors.isEmpty()) return PostMood.NEUTRAL;
        boolean hasBright = colors.stream().anyMatch(this::isBrightColor);
        boolean hasDark = colors.stream().anyMatch(this::isDarkColor);
        if (hasBright && !hasDark) return PostMood.HAPPY;
        if (!hasBright && hasDark) return PostMood.SAD;
        return PostMood.NEUTRAL;
    }

    private boolean isBrightColor(String hex) {
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            return (r + g + b) / 3 > 127;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDarkColor(String hex) {
        return !isBrightColor(hex);
    }

    private UserResponseDTO getAuthenticatedUser() {
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

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}