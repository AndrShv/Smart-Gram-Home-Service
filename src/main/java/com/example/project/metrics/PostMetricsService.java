package com.example.project.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PostMetricsService {

    private final Counter postCreated;
    private final Counter postUpdated;
    private final Counter postDeleted;
    private final Counter postReactionAdded;
    private final Counter postReactionDeleted;
    private final Timer   createPostTimer;
    private final Timer   getPostTimer;

    public PostMetricsService(MeterRegistry registry) {
        postCreated       = Counter.builder("post.created.total").description("Total posts created").register(registry);
        postUpdated       = Counter.builder("post.updated.total").description("Total posts updated").register(registry);
        postDeleted       = Counter.builder("post.deleted.total").description("Total posts deleted").register(registry);
        postReactionAdded = Counter.builder("post.reaction.added.total").description("Total reactions added").register(registry);
        postReactionDeleted = Counter.builder("post.reaction.deleted.total").description("Total reactions deleted").register(registry);
        createPostTimer   = registry.timer("post.create.duration");
        getPostTimer      = registry.timer("post.get.duration");
    }

    public void incrementCreated()         { postCreated.increment(); }
    public void incrementUpdated()         { postUpdated.increment(); }
    public void incrementDeleted()         { postDeleted.increment(); }
    public void incrementReactionAdded()   { postReactionAdded.increment(); }
    public void incrementReactionDeleted() { postReactionDeleted.increment(); }
    public Timer createPostTimer()         { return createPostTimer; }
    public Timer getPostTimer()            { return getPostTimer; }
}