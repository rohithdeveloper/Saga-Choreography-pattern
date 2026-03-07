package com.example.payment_service.service;

import com.example.payment_service.dto.OrderDto;
import com.example.payment_service.dto.PaymentDto;
import com.example.payment_service.dto.ProductDto;
import com.example.payment_service.model.Payment;
import com.example.payment_service.repository.PaymentRepository;
import com.example.payment_service.client.OrderClient;
import com.example.payment_service.client.ProductClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;
    private final ProductClient productClient;

    public PaymentService(PaymentRepository paymentRepository,
                          OrderClient orderClient,
                          ProductClient productClient) {
        this.paymentRepository = paymentRepository;
        this.orderClient = orderClient;
        this.productClient = productClient;
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.order.exchange}")
    private String orderExchange;

    @Value("${rabbitmq.order.routing.cancelled}")
    private String orderCancelledKey;

    @Value("${rabbitmq.order.routing.success}")
    private String paymentSuccessKey;

    // --- AUTOMATED METHOD ONLY ---
    @RabbitListener(queues = "${rabbitmq.queue.stock-deducted}")
    public void handleStockDeducted(OrderDto orderDto) {
        log.info("Stock deducted event received for Order ID: {}", orderDto.getId());
        try {
            ProductDto product = productClient.getProductById(orderDto.getId());
            double actualAmount = orderDto.getQuantity() * product.getPrice();

            PaymentDto paymentDto = new PaymentDto();
            paymentDto.setOrderId(orderDto.getId());
            paymentDto.setAmount(actualAmount);
            paymentDto.setStatus("PENDING");

            processPayment(paymentDto, actualAmount, orderDto);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process automated payment for Order {}. Error: {}",
                    orderDto.getId(), e.getMessage());
        }
    }

    public PaymentDto updatePayment(Long id, PaymentDto dto) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id " + id));
        payment.setOrderId(dto.getOrderId());
        payment.setAmount(dto.getAmount());
        payment.setStatus(dto.getStatus());
        Payment updated = paymentRepository.save(payment);
        return mapToDto(updated);
    }

    public PaymentDto getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found with id " + id));
        return mapToDto(payment);
    }

    public PaymentDto getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId);
        if (payment == null) {
            throw new RuntimeException("Payment not found for orderId " + orderId);
        }
        return mapToDto(payment);
    }

    public List<PaymentDto> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public void deletePayment(Long id) {
        paymentRepository.deleteById(id);
    }

    // ---------------------- External Service Calls ----------------------

    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackGetOrderById")
    public OrderDto getOrderById(Long orderId) {
        return orderClient.getOrderById(orderId);
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "fallbackGetProductById")
    public ProductDto getProductById(Long productId) {
        return productClient.getProductById(productId);
    }

    // ---------------------- Fallbacks ----------------------

    public OrderDto fallbackGetOrderById(Long orderId, Throwable throwable) {
        log.error("Circuit Breaker: Order Service is DOWN. Falling back for Order ID: {}. Reason: {}",
                orderId, throwable.getMessage());
        return new OrderDto(orderId, "Fallback Order", 0);
    }

    public ProductDto fallbackGetProductById(Long productId, Throwable throwable) {
        log.error("Circuit Breaker: Product Service is DOWN. Falling back for Product ID: {}. Reason: {}",
                productId, throwable.getMessage());
        return new ProductDto(productId, "Fallback Product", 0.0,0);
    }

    // ---------------------- Business Logic ----------------------

    public PaymentDto processPayment(PaymentDto dto,double actualAmount,OrderDto orderDto) {
        log.info("Processing payment for Order ID: {}. Amount: {}", dto.getOrderId(), dto.getAmount());
        Payment payment = paymentRepository.findByOrderId(dto.getOrderId());
        if (payment == null) {
            payment = new Payment(dto.getOrderId(), dto.getAmount(), dto.getStatus());
        } else {
            payment.setAmount(dto.getAmount());
            payment.setStatus("PENDING");
        }

        if (dto.getAmount() ==actualAmount) {
            payment.setStatus("SUCCESS");
            rabbitTemplate.convertAndSend(orderExchange, paymentSuccessKey, orderDto.getId());
            log.info("Payment SUCCESS for Order ID: {}. Routing key: {}", orderDto.getId(), paymentSuccessKey);
        } else {
            payment.setStatus("FAILED");
            // COMPENSATION PATH: Publish to rabbitmq.queue.order-cancelled
            // We send the full orderDto so Product Service can restore stock
            rabbitTemplate.convertAndSend(orderExchange, orderCancelledKey, orderDto);
            log.warn("Payment FAILED (Price Mismatch). Expected: {}, Received: {}. Sending Rollback for Order: {}",
                    actualAmount, dto.getAmount(), orderDto.getId());
        }

        Payment saved = paymentRepository.save(payment);
        return mapToDto(saved);
    }

    // ---------------------- Mapper ----------------------
    private PaymentDto mapToDto(Payment payment) {
        return new PaymentDto(payment.getId(),payment.getOrderId(), payment.getAmount(), payment.getStatus());
    }
}
