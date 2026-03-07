package com.example.payment_service.client;

import com.example.payment_service.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

//@FeignClient(name = "order-service", url = "http://localhost:8082") // adjust port
@FeignClient(name="ORDER-MICROSERVICE")
public interface OrderClient {
    @GetMapping("/orders/{id}")
    OrderDto getOrderById(@PathVariable("id") Long id);
}