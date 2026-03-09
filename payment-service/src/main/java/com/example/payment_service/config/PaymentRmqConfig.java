package com.example.payment_service.config;

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

    @Value("${rabbitmq.product.routing.deducted:stock.deducted}")
    private String stockDeductedKey;

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

    // Binding so stock-deducted messages reach this service via the exchange
    @Bean
    public Binding stockDeductedBinding() {
        return BindingBuilder
                .bind(stockDeductedQueue())
                .to(orderExchange())
                .with(stockDeductedKey);
    }

    // 4. JSON Converter
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
        admin.setAutoStartup(true);
        return admin;
    }
}
