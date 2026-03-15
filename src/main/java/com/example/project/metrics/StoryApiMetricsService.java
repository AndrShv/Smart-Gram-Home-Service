package com.example.project.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class StoryApiMetricsService {

    private final Counter createRequests;
    private final Counter getRequests;
    private final Counter deleteRequests;
    private final Counter viewRequests;
    private final Counter reactionRequests;

    public StoryApiMetricsService(MeterRegistry registry) {
        createRequests   = registry.counter("story.api.create.requests");
        getRequests      = registry.counter("story.api.get.requests");
        deleteRequests   = registry.counter("story.api.delete.requests");
        viewRequests     = registry.counter("story.api.view.requests");
        reactionRequests = registry.counter("story.api.reaction.requests");
    }

    public void createRequest()   { createRequests.increment(); }
    public void getRequest()      { getRequests.increment(); }
    public void deleteRequest()   { deleteRequests.increment(); }
    public void viewRequest()     { viewRequests.increment(); }
    public void reactionRequest() { reactionRequests.increment(); }
}