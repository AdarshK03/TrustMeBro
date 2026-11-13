package com.platform.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.orderservice.client.ProductServiceClient;
import com.platform.orderservice.client.ProductServiceClient.ProductDto;
import com.platform.orderservice.client.UserServiceClient;
import com.platform.orderservice.dto.OrderCreateRequest;
import com.platform.orderservice.dto.OrderItemRequest;
import com.platform.orderservice.exception.ServiceUnavailableException;
import com.platform.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private ProductServiceClient productServiceClient;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_ValidPayload_Returns201AndPersistsData() throws Exception {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(10L))
                .thenReturn(Optional.of(new ProductDto(10L, "Wireless Mouse", new BigDecimal("49.99"), 100)));

        OrderCreateRequest request = new OrderCreateRequest(
                1L,
                List.of(new OrderItemRequest(10L, 2))
        );

        mockMvc.perform(post("/api/orders")
                        .header("X-Correlation-ID", "corr-create-order-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(99.98))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(10))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(49.99))
                .andExpect(jsonPath("$.items[0].subtotal").value(99.98));

        assertEquals(1, orderRepository.count());
    }

    @Test
    void createOrder_NonexistentProduct_Returns404AndDoesNotPersist() throws Exception {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(999L)).thenReturn(Optional.empty());

        OrderCreateRequest request = new OrderCreateRequest(
                1L,
                List.of(new OrderItemRequest(999L, 1))
        );

        mockMvc.perform(post("/api/orders")
                        .header("X-Correlation-ID", "corr-404-prod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString("Product not found with id: 999")))
                .andExpect(jsonPath("$.requestId").value("corr-404-prod"));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void createOrder_NonexistentUser_Returns404AndDoesNotPersist() throws Exception {
        when(userServiceClient.verifyUserExists(888L)).thenReturn(false);

        OrderCreateRequest request = new OrderCreateRequest(
                888L,
                List.of(new OrderItemRequest(10L, 1))
        );

        mockMvc.perform(post("/api/orders")
                        .header("X-Correlation-ID", "corr-404-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message", containsString("User not found with id: 888")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void createOrder_StockExceeded_Returns400BadRequestAndDoesNotPersist() throws Exception {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(10L))
                .thenReturn(Optional.of(new ProductDto(10L, "Limited Item", new BigDecimal("20.00"), 5)));

        OrderCreateRequest request = new OrderCreateRequest(
                1L,
                List.of(new OrderItemRequest(10L, 20))
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("exceeds available stock")));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void createOrder_ProductServiceFailure_Returns503AndDoesNotPersist() throws Exception {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(any()))
                .thenThrow(new ServiceUnavailableException("Product Service is currently unreachable. Please try again later."));

        OrderCreateRequest request = new OrderCreateRequest(
                1L,
                List.of(new OrderItemRequest(10L, 1))
        );

        mockMvc.perform(post("/api/orders")
                        .header("X-Correlation-ID", "corr-503-prod")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message", containsString("Product Service is currently unreachable")))
                .andExpect(jsonPath("$.requestId").value("corr-503-prod"));

        assertEquals(0, orderRepository.count());
    }

    @Test
    void createOrder_EmptyItems_Returns400() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(1L, List.of());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createOrder_ZeroOrNegativeQuantity_Returns400() throws Exception {
        OrderCreateRequest request = new OrderCreateRequest(
                1L,
                List.of(new OrderItemRequest(10L, 0))
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getOrderById_Found_Returns200() throws Exception {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(10L))
                .thenReturn(Optional.of(new ProductDto(10L, "Widget", new BigDecimal("15.00"), 50)));

        OrderCreateRequest request = new OrderCreateRequest(1L, List.of(new OrderItemRequest(10L, 3)));
        String responseStr = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseStr).get("id").asLong();

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.totalAmount").value(45.00));
    }

    @Test
    void getOrderById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/orders/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getOrders_WithPagination_ReturnsPage() throws Exception {
        when(userServiceClient.verifyUserExists(1L)).thenReturn(true);
        when(productServiceClient.getProductDetails(10L))
                .thenReturn(Optional.of(new ProductDto(10L, "Widget", new BigDecimal("10.00"), 50)));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderCreateRequest(1L, List.of(new OrderItemRequest(10L, 1))))));
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OrderCreateRequest(1L, List.of(new OrderItemRequest(10L, 2))))));

        mockMvc.perform(get("/api/orders?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }
}
