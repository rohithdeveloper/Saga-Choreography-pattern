package com.example.payment_service.dto;

public class PaymentDto {

    private Long id;        // Payment ID (auto-generated, shown in response)
    private Long orderId;   // Order ID (auto-generated, shown in response)
    private double amount;
    private String status;  // PENDING / SUCCESS / FAILED

    public PaymentDto() {}

    public PaymentDto(Long id, Long orderId, double amount, String status) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
    }

    // ---------------- Getters & Setters ----------------
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
