package com.example.project.metrics;


import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RabbitMetricsService {

    private final Counter rabbitMessagesCounter;

    public RabbitMetricsService(MeterRegistry meterRegistry) {
        this.rabbitMessagesCounter = Counter.builder("rabbit.messages.sent")
                .description("Количество отправленных сообщений в RabbitMQ")
                .register(meterRegistry);
    }

    public void increment() {
        rabbitMessagesCounter.increment();
    }
}
