package com.platform.orderservice.service;

import com.platform.orderservice.dto.OrderCreateRequest;
import com.platform.orderservice.dto.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(OrderCreateRequest request);
    OrderResponse getOrderById(Long id);
    List<OrderResponse> getAllOrders();
    List<OrderResponse> getOrdersByUserId(Long userId);
    Page<OrderResponse> getOrders(Long userId, Pageable pageable);
}
