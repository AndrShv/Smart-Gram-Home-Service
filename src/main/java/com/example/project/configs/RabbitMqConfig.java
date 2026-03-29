package com.example.project.configs;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        factory.setUsername(username);
        factory.setPassword(password);
        return factory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }


    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // --- Exchanges ---
    @Bean
    public TopicExchange homeExchange() {
        return new TopicExchange("home.exchange", true, false);
    }

    @Bean
    public TopicExchange profileExchange() {
        return new TopicExchange("profile.exchange", true, false);
    }
    @Bean
    public TopicExchange authExchange() {
        return new TopicExchange("auth.exchange", true, false);
    }

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("notification.exchange", true, false);
    }



    // --- Queues ---
    @Bean
    public Queue homeQueue() {
        return new Queue("home.smart.gram.queue", true);
    }

    @Bean
    public Queue authQueue() {
        return new Queue("auth.smart.gram.queue", true);
    }
    @Bean
    public Queue profileQueue() {
        return new Queue("profile.smart.gram.queue", true);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue("notification.smart.gram.queue", true);
    }




    // --- Bindings ---
    @Bean
    public Binding bindingHome(Queue homeQueue, TopicExchange homeExchange) {
        return BindingBuilder.bind(homeQueue).to(homeExchange).with("home.#");
    }
    @Bean
    public Binding bindingAuth(Queue authQueue, TopicExchange authExchange) {
        return BindingBuilder.bind(authQueue).to(authExchange).with("auth.#");
    }

    @Bean
    public Binding bindingProfile(Queue profileQueue, TopicExchange profileExchange) {
        return BindingBuilder.bind(profileQueue).to(profileExchange).with("profile.#");
    }
    @Bean
    public Binding bindingNotification(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with("notification.#");
    }

}
