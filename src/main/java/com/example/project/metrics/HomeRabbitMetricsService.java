package com.example.project.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class HomeRabbitMetricsService {

    private final Counter messagesSent;

    public HomeRabbitMetricsService(MeterRegistry registry) {
        messagesSent = registry.counter("home.rabbit.messages.sent");
    }

    public void increment() { messagesSent.increment(); }
}