package com.example.project.service;

import com.example.project.clients.AuthClient;
import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.AiImageResult;
import com.example.project.dto.post.PostDTO;
import com.example.project.dto.count.PostReactionCountDTO;
import com.example.project.dto.profile.SubscriberDTO;
import com.example.project.dto.user.UserResponseDTO;
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

    // ============================================
    //           CRUD ОПЕРАЦИИ
    // ============================================

    @Override
    @Transactional
    public Post createPost(PostDTO post) throws Exception {
        log.info("Создание нового поста");

        return postMetrics.createPostTimer().recordCallable(() -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                throw new UnauthorizedException("JWT истёк");
            }

            UserResponseDTO currentUser = authClient.getCurrentUser();
            UUID userId = UUID.fromString(currentUser.getId());
            log.info("Получен пользователь с ID: {}", userId);

            List<String> generatedTags = new ArrayList<>();
            List<String> dominantColors = new ArrayList<>();
            PostCategory category = PostCategory.OTHER;
            PostMood mood = PostMood.NEUTRAL;

            AiImageResult ai = null;

            try {
                if (post.getPhotoBytes() != null && post.getPhotoBytes().length > 0) {

                    ai = aiImageService.analyzeImage(post.getPhotoBytes());

                    generatedTags = ai.getTags();
                    dominantColors = ai.getColors();

                    category = mapTagsToCategory(generatedTags);
                    mood = mapColorsToMood(dominantColors);
                }
            } catch (Exception e) {
                log.warn("Gemini failed → fallback Imagga");

                try {
                    generatedTags = imaggaService.extractTagsFromBytes(
                            post.getPhotoBytes(), post.getPhotoFileName());

                    dominantColors = imaggaService.extractColorsFromBytes(
                            post.getPhotoBytes(), post.getPhotoFileName());

                    category = mapTagsToCategory(generatedTags);
                    mood = mapColorsToMood(dominantColors);

                } catch (Exception ex) {
                    log.error("Imagga also failed", ex);
                }
            }

            String description = post.getDescription();
            if (description == null || description.isBlank()) {
                description = (ai != null)
                        ? ai.getCaption()
                        : "Фото: " + String.join(", ", generatedTags);
            }

            Post postToSave = Post.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .description(description)
                    .photoUrl(post.getPhotoUrl())
                    .createdAt(LocalDateTime.now())
                    .tags(generatedTags)
                    .dominantColors(dominantColors)
                    .category(category)
                    .mood(mood)
                    .location(post.getLocation())
                    .isPublic(post.isPublic())
                    .reactions(new ArrayList<>())
                    .comments(new ArrayList<>())
                    .build();

            Post savedPost = postRepository.save(postToSave);
            postMetrics.incrementCreated();

            try {
                List<UUID> subscriberIds = subscriptionClient.getFollowers(userId)
                        .stream()
                        .map(SubscriberDTO::getSubscriberUserId)
                        .toList();

                for (UUID subscriberId : subscriberIds) {
                    notificationProducer.sendPostCreated(
                            savedPost.getId().toString(),
                            userId.toString(),
                            subscriberId.toString(),
                            currentUser.getUsername() != null ? currentUser.getUsername() : userId.toString()
                    );
                    rabbitMetrics.increment();
                }
                log.info("Отправлено {} уведомлений о новом посте", subscriberIds.size());
            } catch (Exception e) {
                log.warn("Не удалось отправить уведомления: {}", e.getMessage());
            }

            log.info("Пост успешно сохранён с ID: {}", savedPost.getId());
            return savedPost;
        });
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
        postRepository.save(existingPost);
        postMetrics.incrementUpdated();
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

        UserResponseDTO currentUser = authClient.getCurrentUser();
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

        // Уведомление владельцу поста
        try {
            if (!post.getUserId().equals(userId)) {
                notificationProducer.sendPostLiked(
                        postId.toString(),
                        userId.toString(),
                        post.getUserId().toString(),
                        currentUser.getUsername() != null ? currentUser.getUsername() : userId.toString()
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
        UUID userId = UUID.fromString(authClient.getCurrentUser().getId());

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
                .map(e -> PostReactionCountDTO.builder().reaction(e.getKey()).count(e.getValue()).build())
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
        List<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId);
        posts.forEach(p -> p.setPublic(isPublic));
        postRepository.saveAll(posts);
    }

    public List<Post> searchPosts(String query) {
        String lower = query.toLowerCase();
        return postRepository.findAll().stream()
                .filter(p -> p.isPublic() && p.getDescription() != null
                        && p.getDescription().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public List<Post> searchByTag(String tag) {
        String lower = tag.toLowerCase();
        return postRepository.findAll().stream()
                .filter(p -> p.isPublic() && p.getTags() != null
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
        boolean hasDark   = colors.stream().anyMatch(this::isDarkColor);
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
        } catch (Exception e) { return false; }
    }

    private boolean isDarkColor(String hex) { return !isBrightColor(hex); }
}