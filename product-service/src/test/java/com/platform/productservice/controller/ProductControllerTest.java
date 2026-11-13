package com.platform.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.productservice.dto.ProductCreateRequest;
import com.platform.productservice.dto.ProductUpdateRequest;
import com.platform.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void cleanUp() {
        productRepository.deleteAll();
    }

    @Test
    void createProduct_ValidPayload_Returns201AndPersistsData() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Wireless Gaming Mouse",
                "Ultra-light optical gaming mouse",
                new BigDecimal("49.99"),
                75
        );

        mockMvc.perform(post("/api/products")
                        .header("X-Correlation-ID", "corr-prod-101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Wireless Gaming Mouse"))
                .andExpect(jsonPath("$.description").value("Ultra-light optical gaming mouse"))
                .andExpect(jsonPath("$.price").value(49.99))
                .andExpect(jsonPath("$.stock").value(75))
                .andExpect(jsonPath("$.createdAt").exists());

        // Verify persistence in repository
        assertTrue(productRepository.findByName("Wireless Gaming Mouse").isPresent());
    }

    @Test
    void createProduct_NegativePrice_Returns400BadRequest() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Invalid Item",
                "Description",
                new BigDecimal("-5.00"),
                10
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("price must be greater than zero")));
    }

    @Test
    void createProduct_ZeroPrice_Returns400BadRequest() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Free Item",
                "Description",
                new BigDecimal("0.00"),
                10
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("price must be greater than zero")));
    }

    @Test
    void createProduct_BlankName_Returns400BadRequest() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "",
                "Description",
                new BigDecimal("29.99"),
                10
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Product name is required")));
    }

    @Test
    void createProduct_NegativeStock_Returns400BadRequest() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Valid Name",
                "Description",
                new BigDecimal("29.99"),
                -1
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Product stock must be greater than or equal to zero")));
    }

    @Test
    void getProductById_ExistingProduct_Returns200() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "Mechanical Keyboard",
                "RGB Backlit",
                new BigDecimal("89.99"),
                30
        );
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.price").value(89.99));
    }

    @Test
    void getProductById_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/products/99999")
                        .header("X-Correlation-ID", "corr-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99999"))
                .andExpect(jsonPath("$.requestId").value("corr-not-found"));
    }

    @Test
    void getAllProducts_Returns200WithList() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProductCreateRequest("Item A", "Desc", new BigDecimal("10.00"), 5))));
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProductCreateRequest("Item B", "Desc", new BigDecimal("20.00"), 10))));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateProduct_ExistingProduct_Returns200AndUpdatesData() throws Exception {
        ProductCreateRequest create = new ProductCreateRequest("Old Monitor", "Old Desc", new BigDecimal("199.99"), 15);
        String createResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(createResponse).get("id").asLong();

        ProductUpdateRequest update = new ProductUpdateRequest("4K Curved Monitor", "Updated OLED panel", new BigDecimal("249.99"), 25);

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("4K Curved Monitor"))
                .andExpect(jsonPath("$.description").value("Updated OLED panel"))
                .andExpect(jsonPath("$.price").value(249.99))
                .andExpect(jsonPath("$.stock").value(25));

        // Verify update persisted in DB
        var persisted = productRepository.findById(id).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("4K Curved Monitor", persisted.getName());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("249.99"), persisted.getPrice());
    }

    @Test
    void updateProduct_NotFound_Returns404() throws Exception {
        ProductUpdateRequest update = new ProductUpdateRequest("Item", "Desc", new BigDecimal("10.00"), 5);

        mockMvc.perform(put("/api/products/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateProduct_InvalidPayload_Returns400() throws Exception {
        ProductCreateRequest create = new ProductCreateRequest("Item", "Desc", new BigDecimal("10.00"), 5);
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        ProductUpdateRequest invalidUpdate = new ProductUpdateRequest("", "Desc", new BigDecimal("-1.00"), -5);

        mockMvc.perform(put("/api/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidUpdate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deleteProduct_ExistingProduct_Returns204AndDeletesData() throws Exception {
        ProductCreateRequest create = new ProductCreateRequest("Item to Delete", "Desc", new BigDecimal("15.00"), 5);
        String createResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(delete("/api/products/" + id))
                .andExpect(status().isNoContent());

        // Verify deletion in repository
        assertFalse(productRepository.existsById(id));
    }

    @Test
    void deleteProduct_NotFound_Returns404() throws Exception {
        mockMvc.perform(delete("/api/products/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
