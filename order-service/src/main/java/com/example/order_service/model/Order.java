package com.example.order_service.model;

import jakarta.persistence.*;

@Entity
@Table(name = "orders") // Optional, defaults to class name
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private int quantity;

    /**
     * NEW FIELD: Tracks the Saga state.
     * Common values: PENDING, COMPLETED, CANCELLED
     */
    @Column(nullable = false)
    private String status;

    public Order() {}

    // Updated constructor to include status
    public Order(Long id, String product, int quantity, String status) {
        this.id = id;
        this.product = product;
        this.quantity = quantity;
        this.status = status;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
