package com.example.product_service.dto;

public class OrderDto {
    private Long id;
    private String productName;
    private int quantity;
    private String status;

    public OrderDto() {}

    public OrderDto(Long id, String productName, int quantity, String status) {
        this.id = id;
        this.productName = productName;
        this.quantity = quantity;
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
