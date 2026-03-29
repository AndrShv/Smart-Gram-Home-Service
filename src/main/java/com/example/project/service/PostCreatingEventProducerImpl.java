package com.example.project.service;

import com.example.project.event.PostCreatingNotifications;
import com.example.project.interfaces.PostCreatingEventProducer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Getter
@Setter
public class PostCreatingEventProducerImpl implements PostCreatingEventProducer {
    private final TopicExchange homeExchange;

    private final RabbitTemplate rabbitTemplate;

        public void sendPostCreatingEvent(PostCreatingNotifications event) {
            String routingKey = "home.created";
            rabbitTemplate.convertAndSend(homeExchange.getName(), routingKey, event);
        }
}
