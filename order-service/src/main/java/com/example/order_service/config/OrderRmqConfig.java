package com.example.order_service.config;

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
public class OrderRmqConfig {

    @Value("${rabbitmq.order.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.order-created}")
    private String orderCreatedQueue;

    @Value("${rabbitmq.queue.order-updated}")
    private String orderUpdatedQueue;

    @Value("${rabbitmq.queue.order-deleted}")
    private String orderDeletedQueue;

    // NEW: Saga Orchestration Queues
    @Value("${rabbitmq.queue.payment-success}")
    private String paymentSuccessQueue;

    @Value("${rabbitmq.queue.order-cancelled}")
    private String orderCancelledQueue;

    @Value("${rabbitmq.order.routing.created}")
    private String orderCreatedKey;

    @Value("${rabbitmq.order.routing.updated}")
    private String orderUpdatedKey;

    @Value("${rabbitmq.order.routing.deleted}")
    private String orderDeletedKey;

    // NEW: Saga Routing Keys (Must match Payment Service configuration)
    @Value("${rabbitmq.order.routing.success}")
    private String paymentSuccessKey;

    @Value("${rabbitmq.order.routing.cancelled}")
    private String orderCancelledKey;

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(orderCreatedQueue);
    }

    @Bean
    public Queue orderUpdatedQueue() {
        return new Queue(orderUpdatedQueue);
    }

    @Bean
    public Queue orderDeletedQueue() {
        return new Queue(orderDeletedQueue);
    }

    @Bean
    public Queue paymentSuccessQueue() {
        return new Queue(paymentSuccessQueue);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(orderCancelledQueue);
    }

    @Bean
    public Binding createdBinding() {
        return BindingBuilder
                .bind(orderCreatedQueue())
                .to(orderExchange())
                .with(orderCreatedKey);
    }

    @Bean
    public Binding updatedBinding() {
        return BindingBuilder
                .bind(orderUpdatedQueue())
                .to(orderExchange())
                .with(orderUpdatedKey);
    }

    @Bean
    public Binding deletedBinding() {
        return BindingBuilder
                .bind(orderDeletedQueue())
                .to(orderExchange())
                .with(orderDeletedKey);
    }

    // SAGA HAPPY PATH BINDING: Binds success key to order service success queue
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder
                .bind(paymentSuccessQueue())
                .to(orderExchange())
                .with(paymentSuccessKey);
    }

    // SAGA ROLLBACK BINDING: Binds cancelled key to order service cancellation queue
    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder
                .bind(orderCancelledQueue())
                .to(orderExchange())
                .with(orderCancelledKey);
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