package com.example.product_service.service;

import com.example.product_service.dto.OrderDto;
import com.example.product_service.dto.ProductDto;
import com.example.product_service.model.Product;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.example.product_service.repository.ProductRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.product.exchange}")
    private String productExchange;

    @Value("${rabbitmq.product.routing.deducted}")
    private String stockDeductedKey;

    // Get all products and return as DTOs
    public List<ProductDto> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    // Get product by id and return as DTO
    public ProductDto getProductById(Long id) {
        log.info("Fetching product with ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Product not found with ID: {}", id);
                    return new RuntimeException("Product not found with id " + id);
                });
        return convertToDto(product);
    }

    // Create a new product from DTO
    public ProductDto createProduct(ProductDto productDto) {
        log.info("Creating new product: {}", productDto.getName());
        Product product = convertToEntity(productDto);
        Product savedProduct = productRepository.save(product);
        return convertToDto(savedProduct);
    }

    // Update existing product using DTO
    public ProductDto updateProduct(Long id, ProductDto productDto) {
        log.info("Updating product ID: {}", id);
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id " + id));

        existing.setName(productDto.getName());
        existing.setPrice(productDto.getPrice());

        Product updated = productRepository.save(existing);
        return convertToDto(updated);
    }

    // Delete product by id
    public void deleteProduct(Long id) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id " + id));
        productRepository.delete(existing);
    }


    @RabbitListener(queues = "${rabbitmq.queue.order-created}")
    @Transactional // Very important: ensures the update is saved safely
    public void handleOrderCreated(OrderDto orderDto) {
        log.info("Received Order Created Event for Product: {}", orderDto.getProductName()+ " with "+ orderDto.getId());
        // 1. Find product by name from the event
        Product product = productRepository.findByNameIgnoreCase(orderDto.getProductName())
                .orElseThrow(() -> new RuntimeException("Product not found: " + orderDto.getProductName()));

        // 2. Logic: Check if we have enough
        if (product.getStock() >= orderDto.getQuantity()) {
            // 3. Subtract from current stock
            int updatedStock = product.getStock() - orderDto.getQuantity();
            product.setStock(updatedStock);

            // 4. PERSIST: This saves the new stock value to the DB
            productRepository.save(product);

            // 5. PUBLISH EVENT TO PAYMENT SERVICE
            // We pass the orderDto so Payment knows the orderId and amount to charge
            rabbitTemplate.convertAndSend(productExchange, stockDeductedKey, orderDto);
            log.info("Inventory Deducted. Product: {}, New Stock: {}. Event sent to Payment Service.",
                    product.getName(), updatedStock);
            // Next Step: You could send a 'StockReserved' message to Payment Service here
        } else {
            log.warn("INSUFFICIENT STOCK: Order ID {} requires {} of {}, but only {} available.",
                    orderDto.getId(), orderDto.getQuantity(), product.getName(), product.getStock());            // Handle failure (e.g., notify Order Service that order is cancelled)
        }
    }

    // NEW: Handle Rollback/Cancellation
    @RabbitListener(queues = "${rabbitmq.queue.order-cancelled}")
    @Transactional
    public void handleOrderCancelled(OrderDto orderDto) {
        log.info("Received Compensation Event (Order Cancelled). Restoring stock for: {}", orderDto.getProductName());
        Product product = productRepository.findByNameIgnoreCase(orderDto.getProductName())
                .orElseThrow(() -> {
                    log.error("Failed to restore stock. Product '{}' not found.", orderDto.getProductName());
                    return new RuntimeException("Product not found: " + orderDto.getProductName());
                });

        // Restore the stock
        int restoredStock = product.getStock() + orderDto.getQuantity();
        product.setStock(restoredStock);
        productRepository.save(product);
        log.info("Inventory Restored for {}. New Stock: {}", product.getName(), restoredStock);

    }

    // Helper method: convert entity → DTO
    private ProductDto convertToDto(Product product) {
        return new ProductDto(product.getId(), product.getName(), product.getPrice(),product.getStock());
    }

    // Helper method: convert DTO → entity
    private Product convertToEntity(ProductDto productDto) {
        return new Product(productDto.getId(), productDto.getName(), productDto.getPrice(),productDto.getStock());
    }
}
