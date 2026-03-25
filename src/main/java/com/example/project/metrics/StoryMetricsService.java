package com.example.project.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class StoryMetricsService {

    private final Counter storyCreated;
    private final Counter storyDeleted;
    private final Counter storyViewed;
    private final Counter storyReactionAdded;
    private final Counter storyReactionDeleted;
    private final Timer   createStoryTimer;

    public StoryMetricsService(MeterRegistry registry) {
        storyCreated        = Counter.builder("story.created.total").description("Total stories created").register(registry);
        storyDeleted        = Counter.builder("story.deleted.total").description("Total stories deleted").register(registry);
        storyViewed         = Counter.builder("story.viewed.total").description("Total story views").register(registry);
        storyReactionAdded  = Counter.builder("story.reaction.added.total").description("Total story reactions added").register(registry);
        storyReactionDeleted= Counter.builder("story.reaction.deleted.total").description("Total story reactions deleted").register(registry);
        createStoryTimer    = registry.timer("story.create.duration");
    }

    public void incrementCreated()         { storyCreated.increment(); }
    public void incrementDeleted()         { storyDeleted.increment(); }
    public void incrementViewed()          { storyViewed.increment(); }
    public void incrementReactionAdded()   { storyReactionAdded.increment(); }
    public void incrementReactionDeleted() { storyReactionDeleted.increment(); }
    public Timer createStoryTimer()        { return createStoryTimer; }
}