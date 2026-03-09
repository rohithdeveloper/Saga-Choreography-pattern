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
            // Fetch product by name from Product Service to get the latest unit price
            ProductDto product = productClient.getProductByName(orderDto.getProductName());
            double totalAmount = orderDto.getQuantity() * product.getPrice();

            PaymentDto paymentDto = new PaymentDto();
            paymentDto.setOrderId(orderDto.getId());
            paymentDto.setAmount(totalAmount);
            paymentDto.setStatus("PENDING");

            processPayment(paymentDto, orderDto);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process automated payment for Order {}. Error: {}",
                    orderDto.getId(), e.getMessage());
            // Trigger rollback on any unexpected failure
            rabbitTemplate.convertAndSend(orderExchange, orderCancelledKey, orderDto);
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
        return new OrderDto(orderId, "Fallback Order", 0, "FALLBACK");
    }

    public ProductDto fallbackGetProductById(Long productId, Throwable throwable) {
        log.error("Circuit Breaker: Product Service is DOWN. Falling back for Product ID: {}. Reason: {}",
                productId, throwable.getMessage());
        return new ProductDto(productId, "Fallback Product", 0.0,0);
    }

    // ---------------------- Business Logic ----------------------

    // Simulated max payment limit (replace with real balance/wallet check in production)
    private static final double MAX_PAYMENT_LIMIT = 1000000.0;

    public PaymentDto processPayment(PaymentDto dto, OrderDto orderDto) {
        log.info("Processing payment for Order ID: {}. Amount: {}", dto.getOrderId(), dto.getAmount());
        Payment payment = paymentRepository.findByOrderId(dto.getOrderId());
        if (payment == null) {
            payment = new Payment(dto.getOrderId(), dto.getAmount(), dto.getStatus());
        } else {
            payment.setAmount(dto.getAmount());
            payment.setStatus("PENDING");
        }

        // Real validation: check if amount is within the allowed payment limit
        if (dto.getAmount() <= MAX_PAYMENT_LIMIT) {
            payment.setStatus("SUCCESS");
            rabbitTemplate.convertAndSend(orderExchange, paymentSuccessKey, orderDto.getId());
            log.info("Payment SUCCESS for Order ID: {}. Amount: {}. Routing key: {}",
                    orderDto.getId(), dto.getAmount(), paymentSuccessKey);
        } else {
            payment.setStatus("FAILED");
            // COMPENSATION PATH: Publish cancellation so Product restores stock & Order gets cancelled
            rabbitTemplate.convertAndSend(orderExchange, orderCancelledKey, orderDto);
            log.warn("Payment FAILED (Amount {} exceeds limit {}). Sending Rollback for Order: {}",
                    dto.getAmount(), MAX_PAYMENT_LIMIT, orderDto.getId());
        }

        Payment saved = paymentRepository.save(payment);
        return mapToDto(saved);
    }

    // ---------------------- Mapper ----------------------
    private PaymentDto mapToDto(Payment payment) {
        return new PaymentDto(payment.getId(),payment.getOrderId(), payment.getAmount(), payment.getStatus());
    }
}
