package com.example.order_service.service;

import com.example.order_service.Client.ProductClient;
import com.example.order_service.dto.OrderDto;
import com.example.order_service.dto.ProductDto;
import com.example.order_service.model.Order;
import com.example.order_service.repository.OrderRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private final ProductClient productClient;
    private final OrderRepository orderRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.order.exchange}")
    private String orderExchange;

    @Value("${rabbitmq.order.routing.created}")
    private String orderCreatedKey;

    @Value("${rabbitmq.order.routing.updated}")
    private String orderUpdatedKey;

    @Value("${rabbitmq.order.routing.deleted}")
    private String orderDeletedKey;

    public OrderService(ProductClient productClient,
                        OrderRepository orderRepository,
                        RabbitTemplate rabbitTemplate) {
        this.productClient = productClient;
        this.orderRepository = orderRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    // --- STANDARD CRUD OPERATIONS ---

    public List<OrderDto> getAllOrders() {
        log.info("Fetching all orders from the database");
        return orderRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public OrderDto createOrder(OrderDto dto) {
        Order order = convertToEntity(dto);
        // Step 1 of Saga: Initialize status as PENDING
        order.setStatus("PENDING");
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with ID: {} and status: PENDING", savedOrder.getId());

        // Publish event to start the Saga (Product Service listens to this)
        // IMPORTANT: Send DTO, not entity — entity field "product" won't map to DTO field "productName"
        rabbitTemplate.convertAndSend(
                orderExchange,
                orderCreatedKey,
                convertToDto(savedOrder)
        );
        log.debug("Sent OrderCreated event to exchange: {} with routing key: {}", orderExchange, orderCreatedKey);
        return convertToDto(savedOrder);
    }

    public OrderDto updateOrder(Long id, OrderDto dto) {
        log.info("Updating order with ID: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(
                        () -> {
                            log.error("Update failed: Order with ID {} not found", id);
                            return new RuntimeException("Order not found");
                        }
                );

        order.setProduct(dto.getProductName());
        order.setQuantity(dto.getQuantity());

        Order updatedOrder = orderRepository.save(order);

        rabbitTemplate.convertAndSend(
                orderExchange,
                orderUpdatedKey,
                convertToDto(updatedOrder)
        );

        return convertToDto(updatedOrder);
    }

    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Delete failed: Order with ID {} not found", id);
                    return new RuntimeException("Order not found");
                });

        orderRepository.delete(order);
        log.info("Order with ID: {} deleted successfully", id);
        rabbitTemplate.convertAndSend(
                orderExchange,
                orderDeletedKey,
                convertToDto(order)
        );
    }

    // --- EXTERNAL CALL WITH CIRCUIT BREAKER ---

    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetOrderById")
    public OrderDto getOrderById(Long id) {
        log.info("Fetching order details for ID: {}", id);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // External Feign call to Product Service
        ProductDto product = productClient.getProductById(order.getId());
        log.debug("Received product details from Product Service: {}", product.getName());

        return new OrderDto(order.getId(), product.getName(), order.getQuantity(), order.getStatus());
    }

    // Circuit breaker fallback method
    public OrderDto fallbackGetOrderById(Long id, Throwable throwable) {
        log.warn("Circuit Breaker triggered for Order ID: {}. Reason: {}", id, throwable.getMessage());
        return new OrderDto(id, "Service Unavailable", 0, "CIRCUIT_BREAKER_OPEN");
    }

    // --- SAGA CHOREOGRAPHY LISTENERS ---

    // SAGA HAPPY PATH: Finalize order when Payment Service signals success
    @RabbitListener(queues = "${rabbitmq.queue.payment-success}")
    public void handlePaymentSuccess(Long orderId) {
        log.info("Saga Success Signal: Received payment success for Order ID: {}", orderId);
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            log.info("Order ID: {} status updated to COMPLETED", orderId);        });
    }

    // SAGA COMPENSATION PATH: Cancel order when Payment Service signals failure
    @RabbitListener(queues = "${rabbitmq.queue.order-cancelled}")
    public void handleOrderCancelled(OrderDto orderDto) {
        log.warn("Saga Rollback Signal: Received cancellation for Order ID: {}", orderDto.getId());
        orderRepository.findById(orderDto.getId()).ifPresent(order -> {
            order.setStatus("CANCELLED");
            orderRepository.save(order);
            log.info("Order ID: {} status rolled back to CANCELLED", orderDto.getId());
            System.err.println("Saga Rollback: Order " + orderDto.getId() + " marked as CANCELLED.");
        });
    }

    // --- MAPPING UTILITIES ---

    private OrderDto convertToDto(Order order) {
        return new OrderDto(order.getId(), order.getProduct(), order.getQuantity(), order.getStatus());
    }

    private Order convertToEntity(OrderDto dto) {
        Order order = new Order();
        order.setId(dto.getId());
        order.setProduct(dto.getProductName());
        order.setQuantity(dto.getQuantity());
        order.setStatus(dto.getStatus());
        return order;
    }
}