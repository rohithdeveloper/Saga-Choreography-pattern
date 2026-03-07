package com.example.payment_service.controller;

import com.example.payment_service.dto.OrderDto;
import com.example.payment_service.dto.PaymentDto;
import com.example.payment_service.dto.ProductDto;
import com.example.payment_service.service.PaymentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/{id}")
    public PaymentDto getPayment(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping
    public List<PaymentDto> getAllPayments() {
        return paymentService.getAllPayments();
    }

    @PutMapping("/{id}")
    public PaymentDto updatePayment(@PathVariable Long id, @RequestBody PaymentDto dto) {
        return paymentService.updatePayment(id, dto);
    }

    @DeleteMapping("/{id}")
    public String deletePayment(@PathVariable Long id) {
        paymentService.deletePayment(id);
        return "Payment deleted successfully";
    }

    @GetMapping("/order/{orderId}")
    public OrderDto getOrderById(@PathVariable Long orderId) {
        return paymentService.getOrderById(orderId);
    }

    @GetMapping("/product/{productId}")
    public ProductDto getProductById(@PathVariable Long productId) {
        return paymentService.getProductById(productId);
    }
}