package com.platform.orderservice.client;

import com.platform.orderservice.client.ProductServiceClient.ProductDto;
import com.platform.orderservice.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpClientsTest {

    private MockRestServiceServer mockServer;
    private HttpProductServiceClient productServiceClient;
    private HttpUserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();

        productServiceClient = new HttpProductServiceClient(builder, "http://localhost:8082");
        userServiceClient = new HttpUserServiceClient(builder, "http://localhost:8081");
    }

    @Test
    void productServiceClient_Success_ReturnsProductDto() {
        String json = """
                {
                    "id": 5,
                    "name": "Mechanical Keyboard",
                    "price": 89.99,
                    "stock": 42
                }
                """;

        mockServer.expect(requestTo("http://localhost:8082/api/products/5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<ProductDto> result = productServiceClient.getProductDetails(5L);

        assertTrue(result.isPresent());
        assertEquals(5L, result.get().id());
        assertEquals("Mechanical Keyboard", result.get().name());
        assertEquals(new BigDecimal("89.99"), result.get().price());
        assertEquals(42, result.get().stock());
        mockServer.verify();
    }

    @Test
    void productServiceClient_NotFound_ReturnsEmptyOptional() {
        mockServer.expect(requestTo("http://localhost:8082/api/products/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<ProductDto> result = productServiceClient.getProductDetails(999L);

        assertTrue(result.isEmpty());
        mockServer.verify();
    }

    @Test
    void productServiceClient_ServerError_ThrowsServiceUnavailableException() {
        mockServer.expect(requestTo("http://localhost:8082/api/products/5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThrows(ServiceUnavailableException.class, () -> productServiceClient.getProductDetails(5L));
        mockServer.verify();
    }

    @Test
    void userServiceClient_Success_ReturnsTrue() {
        mockServer.expect(requestTo("http://localhost:8081/api/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"Alice\"}", MediaType.APPLICATION_JSON));

        boolean exists = userServiceClient.verifyUserExists(1L);

        assertTrue(exists);
        mockServer.verify();
    }

    @Test
    void userServiceClient_NotFound_ReturnsFalse() {
        mockServer.expect(requestTo("http://localhost:8081/api/users/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        boolean exists = userServiceClient.verifyUserExists(999L);

        assertFalse(exists);
        mockServer.verify();
    }

    @Test
    void userServiceClient_ServerError_ThrowsServiceUnavailableException() {
        mockServer.expect(requestTo("http://localhost:8081/api/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertThrows(ServiceUnavailableException.class, () -> userServiceClient.verifyUserExists(1L));
        mockServer.verify();
    }
}

