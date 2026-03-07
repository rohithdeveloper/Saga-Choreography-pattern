package com.example.payment_service.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRmqConfig {

    // --- Queue Names from properties ---
    @Value("${rabbitmq.queue.stock-deducted}")
    private String stockDeductedQueue;

    @Value("${rabbitmq.queue.payment-success}")
    private String paymentSuccessQueue;

    @Value("${rabbitmq.queue.order-cancelled}")
    private String orderCancelledQueue;

    // --- Exchange and Routing Keys ---
    @Value("${rabbitmq.order.exchange}")
    private String exchange;

    @Value("${rabbitmq.order.routing.success}")
    private String paymentSuccessKey;

    @Value("${rabbitmq.order.routing.cancelled}")
    private String orderCancelledKey;

    // 1. Declare the Exchange
    // This acts as the central router for your Order events
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(exchange);
    }

    // 2. Declare Queues
    // stockDeductedQueue: Payment Service listens here to start its task
    @Bean
    public Queue stockDeductedQueue() {
        return new Queue(stockDeductedQueue);
    }

    // paymentSuccessQueue: Order Service will listen here for the Happy Path
    @Bean
    public Queue paymentSuccessQueue() {
        return new Queue(paymentSuccessQueue);
    }

    // orderCancelledQueue: Product & Order services listen here for Rollbacks
    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(orderCancelledQueue);
    }

    // 3. Declare Bindings
    // These connect the Exchange to the Queues via Routing Keys so messages find their way
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder
                .bind(paymentSuccessQueue())
                .to(orderExchange())
                .with(paymentSuccessKey);
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder
                .bind(orderCancelledQueue())
                .to(orderExchange())
                .with(orderCancelledKey);
    }

    // 4. JSON Converter
    // Ensures DTO objects are converted to JSON for the RabbitMQ broker
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
