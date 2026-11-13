package com.platform.orderservice.service;

import com.platform.orderservice.client.ProductServiceClient;
import com.platform.orderservice.client.UserServiceClient;
import com.platform.orderservice.dto.OrderCreateRequest;
import com.platform.orderservice.dto.OrderItemRequest;
import com.platform.orderservice.dto.OrderResponse;
import com.platform.orderservice.exception.ResourceNotFoundException;
import com.platform.orderservice.model.Order;
import com.platform.orderservice.model.OrderItem;
import com.platform.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;

    public OrderServiceImpl(OrderRepository orderRepository,
                            UserServiceClient userServiceClient,
                            ProductServiceClient productServiceClient) {
        this.orderRepository = orderRepository;
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
    }

    @Override
    public OrderResponse createOrder(OrderCreateRequest request) {
        // Inter-service verification: verify user exists
        if (!userServiceClient.verifyUserExists(request.getUserId())) {
            throw new ResourceNotFoundException("User not found with id: " + request.getUserId());
        }

        Order order = new Order(request.getUserId(), "CREATED", BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.getItems()) {
            // Inter-service verification: verify product details and live price
            ProductServiceClient.ProductDto product = productServiceClient.getProductDetails(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId()));

            OrderItem item = new OrderItem(product.id(), itemReq.getQuantity(), product.price());
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        return OrderResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return OrderResponse.fromEntity(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(OrderResponse::fromEntity)
                .collect(Collectors.toList());
    }
}
