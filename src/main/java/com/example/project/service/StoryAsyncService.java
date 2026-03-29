package com.example.project.service;

import com.example.project.clients.SubscriptionClient;
import com.example.project.dto.AiImageResult;
import com.example.project.dto.SubscriberDTO;
import com.example.project.entity.Story;
import com.example.project.enums.StoryCategory;
import com.example.project.enums.StoryMood;
import com.example.project.interfaces.AiImageService;
import com.example.project.metrics.HomeRabbitMetricsService;
import com.example.project.metrics.StoryMetricsService;
import com.example.project.repository.StoryRepository;
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
public class StoryAsyncService {
    private final SubscriptionClient subscriptionClient;
    private final StoryRepository storyRepository;
    private final ExecutorService virtualThreadExecutor;
    private final AiImageService aiImageService;
    private final NotificationProducer notificationProducer;
    private final StoryMetricsService storyMetrics;
    private final HomeRabbitMetricsService rabbitMetrics;


    public void createStoryAsync(Story storyDto, UUID storyId) {
        CompletableFuture.runAsync(() -> {
            storyMetrics.createStoryTimer().record(() -> {
                log.info("Async story processing started for story {}", storyId);

                try {
                    if (storyDto.getPhotoBytes() == null || storyDto.getPhotoBytes().length == 0) {
                        log.warn("Story {} has no photo bytes, skipping AI enrichment", storyId);
                        return;
                    }

                    Optional<Story> optionalStory = storyRepository.findById(storyId);
                    if (optionalStory.isEmpty()) {
                        log.warn("Story {} not found for async enrichment", storyId);
                        return;
                    }

                    Story story = optionalStory.get();

                    AiImageResult aiImageResult = null;
                    try {
                        aiImageResult = aiImageService.analyzeImage(storyDto.getPhotoBytes());
                    } catch (Exception e) {
                        log.error("AI analysis failed for story {}: {}", storyId, e.getMessage(), e);
                    }

                    List<String> tags = aiImageResult != null ? aiImageResult.getTags() : new ArrayList<>();
                    StoryCategory category = aiImageResult != null
                            ? mapTagsToCategoryStory(tags)
                            : StoryCategory.OTHER;

                    StoryMood mood = aiImageResult != null
                            ? mapColorsToMoodStory(aiImageResult.getColors())
                            : StoryMood.NEUTRAL;

                    story.setTags(tags);
                    story.setCategory(category);
                    story.setMood(mood);
                    storyRepository.save(story);

                    log.info("Async story processing completed for story {}", storyId);

                } catch (Exception e) {
                    log.error("Error during async story processing for story {}", storyId, e);
                }
            });
        }, virtualThreadExecutor);
    }

    public CompletableFuture<Void> sendNotificationsAsync(Story savedStory, UUID userId, String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<UUID> subscriberIds = subscriptionClient.getFollowers(userId)
                        .stream()
                        .map(SubscriberDTO::getSubscriberUserId)
                        .toList();

                for (UUID subscriberId : subscriberIds) {
                    notificationProducer.sendStoryCreated(
                            savedStory.getId().toString(),
                            userId.toString(),
                            subscriberId.toString(),
                            username != null ? username : userId.toString()
                    );
                    rabbitMetrics.increment();
                }
                log.info("Async story notification sent for story {} to {} subscribers", savedStory.getId(), subscriberIds.size());

            } catch (Exception e) {
                log.error("Error during async story notification for story {}", savedStory.getId(), e);
            }
        }, virtualThreadExecutor);
    }

    private StoryCategory mapTagsToCategoryStory(List<String> tags) {
        if (tags == null || tags.isEmpty()) return StoryCategory.OTHER;

        List<String> lowerTags = tags.stream()
                .map(String::toLowerCase)
                .toList();

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("travel") || tag.contains("paris") || tag.contains("eiffel") ||
                        tag.contains("landmark") || tag.contains("tourism") || tag.contains("beach") ||
                        tag.contains("mountain") || tag.contains("vacation") || tag.contains("city"))) {
            return StoryCategory.TRAVEL;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("food") || tag.contains("meal") || tag.contains("drink") ||
                        tag.contains("coffee") || tag.contains("restaurant"))) {
            return StoryCategory.FOOD;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("sport") || tag.contains("fitness") || tag.contains("gym") ||
                        tag.contains("football") || tag.contains("basketball") || tag.contains("yoga"))) {
            return StoryCategory.FITNESS;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("nature") || tag.contains("forest") || tag.contains("tree") ||
                        tag.contains("flower") || tag.contains("sky") || tag.contains("park") ||
                        tag.contains("river") || tag.contains("mountain"))) {
            return StoryCategory.NATURE;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("music") || tag.contains("song") || tag.contains("concert") ||
                        tag.contains("band") || tag.contains("instrument"))) {
            return StoryCategory.MUSIC;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("art") || tag.contains("painting") || tag.contains("drawing") ||
                        tag.contains("museum") || tag.contains("sculpture"))) {
            return StoryCategory.ART;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("family") || tag.contains("kids") || tag.contains("parents"))) {
            return StoryCategory.FAMILY;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("friend") || tag.contains("party") || tag.contains("hangout"))) {
            return StoryCategory.FRIENDS;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("celebrity") || tag.contains("star") || tag.contains("movie"))) {
            return StoryCategory.CELEBRITY;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("personal") || tag.contains("diary") || tag.contains("self"))) {
            return StoryCategory.PERSONAL;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("work") || tag.contains("office") || tag.contains("job"))) {
            return StoryCategory.WORK;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("hobby") || tag.contains("craft") || tag.contains("collection"))) {
            return StoryCategory.HOBBY;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("fashion") || tag.contains("style") || tag.contains("clothes") ||
                        tag.contains("outfit"))) {
            return StoryCategory.FASHION;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("technology") || tag.contains("tech") || tag.contains("gadget") ||
                        tag.contains("device"))) {
            return StoryCategory.TECHNOLOGY;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("education") || tag.contains("school") || tag.contains("learning") ||
                        tag.contains("study"))) {
            return StoryCategory.EDUCATION;
        }

        if (lowerTags.stream().anyMatch(tag ->
                tag.contains("health") || tag.contains("doctor") || tag.contains("fitness") ||
                        tag.contains("wellness"))) {
            return StoryCategory.HEALTH;
        }

        return StoryCategory.OTHER;
    }

    private StoryMood mapColorsToMoodStory(List<String> colors) {
        if (colors == null || colors.isEmpty()) return StoryMood.NEUTRAL;

        String joined = String.join(" ", colors).toLowerCase();

        if (joined.contains("yellow") || joined.contains("orange") || joined.contains("pink")) {
            return StoryMood.HAPPY;
        }

        if (joined.contains("blue") || joined.contains("navy") || joined.contains("darkblue")) {
            return StoryMood.SAD;
        }

        if (joined.contains("red") || joined.contains("magenta") || joined.contains("crimson")) {
            return StoryMood.ENERGETIC;
        }

        if (joined.contains("black") || joined.contains("gray") || joined.contains("dark")) {
            return StoryMood.ANGRY;
        }

        if (joined.contains("brown") || joined.contains("beige") || joined.contains("taupe")) {
            return StoryMood.BORED;
        }

        if (joined.contains("purple") || joined.contains("violet") || joined.contains("lime")) {
            return StoryMood.NERVOUS;
        }

        if (joined.contains("green") || joined.contains("forest") || joined.contains("olive")) {
            return StoryMood.HOPEFUL;
        }

        if (joined.contains("white") || joined.contains("cream") || joined.contains("ivory")) {
            return StoryMood.PROUD;
        }

        if (joined.contains("steelblue") || joined.contains("slate") || joined.contains("gray")) {
            return StoryMood.LONELY;
        }

        if (joined.contains("cyan") || joined.contains("turquoise") || joined.contains("aqua")) {
            return StoryMood.EXCITED;
        }
        return StoryMood.NEUTRAL;
    }
}


