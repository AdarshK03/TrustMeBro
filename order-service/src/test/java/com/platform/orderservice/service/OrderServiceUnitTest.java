package com.platform.orderservice.service;

import com.platform.orderservice.client.ProductServiceClient;
import com.platform.orderservice.client.ProductServiceClient.ProductDto;
import com.platform.orderservice.client.UserServiceClient;
import com.platform.orderservice.dto.OrderCreateRequest;
import com.platform.orderservice.dto.OrderItemRequest;
import com.platform.orderservice.dto.OrderResponse;
import com.platform.orderservice.exception.ResourceNotFoundException;
import com.platform.orderservice.exception.ServiceUnavailableException;
import com.platform.orderservice.model.Order;
import com.platform.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private ProductDto sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new ProductDto(10L, "Premium Headphones", new BigDecimal("199.99"), 50);
    }

    @Test
    void createOrder_Success_AuthoritativePricingAndPersistence() {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(10L)).thenReturn(Optional.of(sampleProduct));

        Order savedMock = new Order(1L, "CREATED", new BigDecimal("399.98"));
        savedMock.setId(100L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(100L);
            return o;
        });

        OrderCreateRequest request = new OrderCreateRequest(1L, List.of(new OrderItemRequest(10L, 2)));
        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(new BigDecimal("399.98"), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("199.99"), response.getItems().get(0).getUnitPrice());
        assertEquals(new BigDecimal("399.98"), response.getItems().get(0).getSubtotal());

        verify(userServiceClient).verifyUserExists(1L);
        verify(productServiceClient).getProductDetails(10L);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_UserNotFound_ThrowsExceptionAndNeverPersists() {
        when(userServiceClient.verifyUserExists(999L)).thenReturn(false);

        OrderCreateRequest request = new OrderCreateRequest(999L, List.of(new OrderItemRequest(10L, 1)));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("User not found with id: 999"));

        verify(userServiceClient).verifyUserExists(999L);
        verifyNoInteractions(productServiceClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createOrder_ProductNotFound_ThrowsExceptionAndNeverPersists() {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(999L)).thenReturn(Optional.empty());

        OrderCreateRequest request = new OrderCreateRequest(1L, List.of(new OrderItemRequest(999L, 1)));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("Product not found with id: 999"));

        verify(userServiceClient).verifyUserExists(1L);
        verify(productServiceClient).getProductDetails(999L);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createOrder_QuantityExceedsStock_ThrowsIllegalArgumentException() {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        ProductDto lowStock = new ProductDto(10L, "Low Stock Item", new BigDecimal("10.00"), 3);
        when(productServiceClient.getProductDetails(10L)).thenReturn(Optional.of(lowStock));

        OrderCreateRequest request = new OrderCreateRequest(1L, List.of(new OrderItemRequest(10L, 10)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("exceeds available stock"));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_ProductServiceUnavailable_PropagatesException() {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(10L))
                .thenThrow(new ServiceUnavailableException("Product Service unreachable"));

        OrderCreateRequest request = new OrderCreateRequest(1L, List.of(new OrderItemRequest(10L, 1)));

        assertThrows(ServiceUnavailableException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrderById_Found() {
        Order mockOrder = new Order(1L, "CREATED", new BigDecimal("50.00"));
        mockOrder.setId(42L);
        when(orderRepository.findById(42L)).thenReturn(Optional.of(mockOrder));

        OrderResponse res = orderService.getOrderById(42L);
        assertNotNull(res);
        assertEquals(42L, res.getId());
        assertEquals(1L, res.getUserId());
    }

    @Test
    void getOrderById_NotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderById(999L));
    }

    @Test
    void getOrders_WithPagination() {
        Order o1 = new Order(1L, "CREATED", new BigDecimal("10.00"));
        o1.setId(1L);
        Page<Order> paged = new PageImpl<>(List.of(o1));
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(pageable)).thenReturn(paged);

        Page<OrderResponse> result = orderService.getOrders(null, pageable);
        assertEquals(1, result.getTotalElements());
        assertEquals(1L, result.getContent().get(0).getId());
    }
}

