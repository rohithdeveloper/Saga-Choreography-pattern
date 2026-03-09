package com.example.product_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductRmqConfig {

    @Value("${rabbitmq.order.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.order-created}")
    private String orderCreatedQueue;

    @Value("${rabbitmq.order.routing.created}")
    private String orderCreatedKey;

    @Value("${rabbitmq.queue.order-cancelled}")
    private String orderCancelledQueue;

    @Value("${rabbitmq.order.routing.cancelled}")
    private String orderCancelledKey;

    @Value("${rabbitmq.product.exchange}")
    private String productExchange;

    @Value("${rabbitmq.queue.stock-deducted}")
    private String stockDeductedQueue;

    @Value("${rabbitmq.product.routing.deducted}")
    private String stockDeductedKey;

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(orderCreatedQueue);
    }

    @Bean
    public Binding createdBinding() {
        return BindingBuilder
                .bind(orderCreatedQueue())
                .to(orderExchange())
                .with(orderCreatedKey);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(orderCancelledQueue);
    }

    @Bean
    public Binding cancelledBinding() {
        return BindingBuilder
                .bind(orderCancelledQueue())
                .to(orderExchange())
                .with(orderCancelledKey);
    }

    @Bean
    public DirectExchange productExchange() {
        return new DirectExchange(productExchange);
    }

    @Bean
    public Queue stockDeductedQueue() {
        return new Queue(stockDeductedQueue);
    }

    @Bean
    public Binding stockDeductedBinding() {

        return BindingBuilder
                .bind(stockDeductedQueue())
                .to(productExchange())
                .with(stockDeductedKey);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // This forces the admin to declare all Queue, Exchange, and Binding beans
        admin.setAutoStartup(true);
        return admin;
    }
}