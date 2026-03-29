package com.example.project.service;


import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.AiImageResult;
import com.example.project.dto.PostDTO;
import com.example.project.dto.SubscriberDTO;
import com.example.project.entity.Post;
import com.example.project.enums.PostCategory;
import com.example.project.enums.PostMood;
import com.example.project.interfaces.AiImageService;
import com.example.project.interfaces.ImaggaService;
import com.example.project.metrics.PostMetricsService;
import com.example.project.metrics.RabbitMetricsService;
import com.example.project.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostAsyncService {
    private final AiImageService aiImageService;
    private final ImaggaService imaggaService;
    private final PostRepository postRepository;
    private final SubscriptionClient subscriptionClient;
    private final NotificationProducer notificationProducer;
    private final RabbitMetricsService rabbitMetrics;
    private final ExecutorService virtualThreadExecutor;
    private final PostMetricsService postMetricsService;

    public void createPostAsync(UUID postId, PostDTO postDto, UUID userId) {
        CompletableFuture.runAsync(() -> {
            postMetricsService.createPostTimer().record(() -> {
                try {
                    if (postDto.getPhotoBytes() == null || postDto.getPhotoBytes().length == 0) {
                        log.warn("Post {} has no photo bytes, skipping AI enrichment", postId);
                        return;
                    }

                    Optional<Post> optionalPost = postRepository.findById(postId);
                    if (optionalPost.isEmpty()) {
                        log.warn("Post {} not found for async enrichment", postId);
                        return;
                    }

                    Post post = optionalPost.get();

                    AiImageResult ai = null;
                    List<String> generatedTags = new ArrayList<>();
                    List<String> dominantColors = new ArrayList<>();
                    PostCategory category = PostCategory.OTHER;
                    PostMood mood = PostMood.NEUTRAL;
                    String description = post.getDescription();

                    try {
                        ai = aiImageService.analyzeImage(postDto.getPhotoBytes());

                        generatedTags = ai.getTags();
                        dominantColors = ai.getColors();
                        category = mapTagsToCategoryPost(generatedTags);
                        mood = mapColorsToMoodPost(dominantColors);

                        log.info("AI analysis success for post {}", postId);

                    } catch (Exception e) {
                        log.warn("AI analysis failed for post {}: {}", postId, e.getMessage());

                        try {
                            generatedTags = imaggaService.extractTagsFromBytes(
                                    postDto.getPhotoBytes(), postDto.getPhotoFileName());

                            dominantColors = imaggaService.extractColorsFromBytes(
                                    postDto.getPhotoBytes(), postDto.getPhotoFileName());

                            category = mapTagsToCategoryPost(generatedTags);
                            mood = mapColorsToMoodPost(dominantColors);

                            log.info("Imagga fallback success for post {}", postId);

                        } catch (Exception ex) {
                            log.error("Imagga analysis also failed for post {}: {}", postId, ex.getMessage(), ex);
                        }
                    }

                    if (description == null || description.isBlank()) {
                        description = (ai != null && ai.getCaption() != null && !ai.getCaption().isBlank())
                                ? ai.getCaption()
                                : "Фото: " + String.join(", ", generatedTags);
                    }

                    post.setUserId(userId);
                    post.setDescription(description);
                    post.setTags(generatedTags);
                    post.setDominantColors(dominantColors);
                    post.setCategory(category);
                    post.setMood(mood);

                    postRepository.save(post);

                    log.info("Post {} enriched successfully", postId);

                } catch (Exception e) {
                    log.error("Error enriching post {}: {}", postId, e.getMessage(), e);
                }
            });
        }, virtualThreadExecutor);
    }

    public void sendNotificationsAsync(Post savedPost, UUID userId, String username) {
        CompletableFuture.runAsync(() -> {
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
                            username != null ? username : userId.toString()
                    );
                    rabbitMetrics.increment();
                }
                log.info("Отправлено {} уведомлений о новом посте", subscriberIds.size());

            } catch (Exception e) {
                log.warn("Не удалось отправить уведомления: {}", e.getMessage());
            }
        }, virtualThreadExecutor);

    }

    private PostCategory mapTagsToCategoryPost(List<String> tags) {
        if (tags == null || tags.isEmpty()) return PostCategory.OTHER;

        List<String> lowerTags = tags.stream()
                .map(String::toLowerCase)
                .toList();

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("travel") || tag.contains("paris") || tag.contains("eiffel") ||
                        tag.contains("landmark") || tag.contains("tourism") || tag.contains("beach") ||
                        tag.contains("mountain") || tag.contains("vacation") || tag.contains("city"))) {
            return PostCategory.TRAVEL;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("food") || tag.contains("meal") || tag.contains("drink") ||
                        tag.contains("coffee") || tag.contains("restaurant") || tag.contains("dinner") ||
                        tag.contains("lunch") || tag.contains("breakfast") || tag.contains("dessert"))) {
            return PostCategory.FOOD;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("sport") || tag.contains("fitness") || tag.contains("gym") ||
                        tag.contains("football") || tag.contains("basketball") || tag.contains("soccer") ||
                        tag.contains("tennis") || tag.contains("swim") || tag.contains("yoga"))) {
            return PostCategory.SPORTS;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("nature") || tag.contains("forest") || tag.contains("tree") ||
                        tag.contains("flower") || tag.contains("sky") || tag.contains("river") ||
                        tag.contains("mountain") || tag.contains("landscape"))) {
            return PostCategory.NATURE;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("fashion") || tag.contains("style") || tag.contains("clothes") ||
                        tag.contains("makeup") || tag.contains("beauty") || tag.contains("lifestyle"))) {
            return PostCategory.LIFESTYLE;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("fashion") || tag.contains("design") || tag.contains("trend") ||
                        tag.contains("model") || tag.contains("runway"))) {
            return PostCategory.FASHION;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("health") || tag.contains("wellness") || tag.contains("medicine") ||
                        tag.contains("mental") || tag.contains("workout") || tag.contains("nutrition"))) {
            return PostCategory.HEALTH;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("education") || tag.contains("school") || tag.contains("study") ||
                        tag.contains("learning") || tag.contains("university") || tag.contains("course"))) {
            return PostCategory.EDUCATION;
        }
        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("movie") || tag.contains("music") || tag.contains("concert") ||
                        tag.contains("game") || tag.contains("tv") || tag.contains("show") ||
                        tag.contains("entertainment"))) {
            return PostCategory.ENTERTAINMENT;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("business") || tag.contains("startup") || tag.contains("company") ||
                        tag.contains("finance") || tag.contains("marketing") || tag.contains("money"))) {
            return PostCategory.BUSINESS;
        }

        return PostCategory.OTHER;
    }

    private PostMood mapColorsToMoodPost(List<String> colors) {
        if (colors == null || colors.isEmpty()) return PostMood.NEUTRAL;

        String joined = String.join(" ", colors).toLowerCase();

        if (joined.contains("yellow") || joined.contains("orange") || joined.contains("pink")) return PostMood.HAPPY;
        if (joined.contains("blue") || joined.contains("lightblue") || joined.contains("cyan")) return PostMood.CALM;
        if (joined.contains("red") || joined.contains("magenta") || joined.contains("crimson")) return PostMood.ENERGETIC;
        if (joined.contains("black") || joined.contains("gray") || joined.contains("dark")) return PostMood.MYSTERIOUS;
        if (joined.contains("green") || joined.contains("forest") || joined.contains("olive") || joined.contains("brown")) return PostMood.HOPEFUL;
        if (joined.contains("purple") || joined.contains("violet")) return PostMood.EXCITED;
        if (joined.contains("beige") || joined.contains("tan")) return PostMood.BORED;
        if (joined.contains("white") || joined.contains("cream") || joined.contains("ivory")) return PostMood.PROUD;
        if (joined.contains("navy") || joined.contains("steelblue") || joined.contains("slate")) return PostMood.LONELY;
        if (joined.contains("lime") || joined.contains("teal")) return PostMood.NERVOUS;
        if (joined.contains("brown") || joined.contains("ochre")) return PostMood.ANGRY;
        if (joined.contains("gold") || joined.contains("orange")) return PostMood.HOPEFUL;

        return PostMood.NEUTRAL;
    }
}


