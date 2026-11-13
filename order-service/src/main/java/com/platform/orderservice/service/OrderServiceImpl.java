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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

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
        log.info("Initiating order creation for userId={}", request.getUserId());

        // 1. Inter-service verification: verify user exists in User Service
        if (!userServiceClient.verifyUserExists(request.getUserId())) {
            log.warn("User validation failed: user with id {} does not exist", request.getUserId());
            throw new ResourceNotFoundException("User not found with id: " + request.getUserId());
        }

        Order order = new Order(request.getUserId(), "CREATED", BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;

        // 2. Validate items and verify products via Product Service
        for (OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getQuantity() == null || itemReq.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be at least 1");
            }

            // Fetch authoritative details and price directly from Product Service
            ProductServiceClient.ProductDto product = productServiceClient.getProductDetails(itemReq.getProductId())
                    .orElseThrow(() -> {
                        log.warn("Product validation failed: product with id {} does not exist", itemReq.getProductId());
                        return new ResourceNotFoundException("Product not found with id: " + itemReq.getProductId());
                    });

            // Validate inventory availability
            if (product.stock() != null && itemReq.getQuantity() > product.stock()) {
                log.warn("Insufficient stock for productId={}: requested={}, available={}",
                        itemReq.getProductId(), itemReq.getQuantity(), product.stock());
                throw new IllegalArgumentException(String.format(
                        "Requested quantity %d exceeds available stock %d for product ID: %d",
                        itemReq.getQuantity(), product.stock(), product.id()));
            }

            // Create order item using authoritative price from Product Service
            OrderItem item = new OrderItem(product.id(), itemReq.getQuantity(), product.price());
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        log.info("Order successfully created with id={}, totalAmount={}", saved.getId(), saved.getTotalAmount());
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

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Long userId, Pageable pageable) {
        if (userId != null) {
            return orderRepository.findByUserId(userId, pageable).map(OrderResponse::fromEntity);
        }
        return orderRepository.findAll(pageable).map(OrderResponse::fromEntity);
    }
}
