package com.example.project.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PostApiMetricsService {

    private final Counter createRequests;
    private final Counter getRequests;
    private final Counter updateRequests;
    private final Counter deleteRequests;
    private final Counter feedRequests;
    private final Counter searchRequests;

    public PostApiMetricsService(MeterRegistry registry) {
        createRequests = registry.counter("post.api.create.requests");
        getRequests    = registry.counter("post.api.get.requests");
        updateRequests = registry.counter("post.api.update.requests");
        deleteRequests = registry.counter("post.api.delete.requests");
        feedRequests   = registry.counter("post.api.feed.requests");
        searchRequests = registry.counter("post.api.search.requests");
    }

    public void createRequest() { createRequests.increment(); }
    public void getRequest()    { getRequests.increment(); }
    public void updateRequest() { updateRequests.increment(); }
    public void deleteRequest() { deleteRequests.increment(); }
    public void feedRequest()   { feedRequests.increment(); }
    public void searchRequest() { searchRequests.increment(); }
}