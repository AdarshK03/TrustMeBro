package com.platform.productservice.service;

import com.platform.productservice.dto.ProductCreateRequest;
import com.platform.productservice.dto.ProductResponse;
import com.platform.productservice.dto.ProductUpdateRequest;
import com.platform.productservice.exception.ResourceNotFoundException;
import com.platform.productservice.model.Product;
import com.platform.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product("Mechanical Keyboard", "RGB Gaming", new BigDecimal("99.99"), 50);
        sampleProduct.setId(1L);
        sampleProduct.setCreatedAt(Instant.now());
        sampleProduct.setUpdatedAt(Instant.now());
    }

    @Test
    void createProduct_Success() {
        ProductCreateRequest request = new ProductCreateRequest("Mechanical Keyboard", "RGB Gaming", new BigDecimal("99.99"), 50);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Mechanical Keyboard", response.getName());
        assertEquals(new BigDecimal("99.99"), response.getPrice());
        assertEquals(50, response.getStock());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void getProductById_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductResponse response = productService.getProductById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Mechanical Keyboard", response.getName());
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    void getProductById_NotFound_ThrowsResourceNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.getProductById(999L)
        );

        assertTrue(exception.getMessage().contains("Product not found with id: 999"));
        verify(productRepository, times(1)).findById(999L);
    }

    @Test
    void getAllProducts_ReturnsList() {
        Product prod2 = new Product("Wireless Mouse", "Optical", new BigDecimal("29.99"), 100);
        prod2.setId(2L);
        when(productRepository.findAll()).thenReturn(List.of(sampleProduct, prod2));

        List<ProductResponse> responses = productService.getAllProducts();

        assertEquals(2, responses.size());
        assertEquals("Mechanical Keyboard", responses.get(0).getName());
        assertEquals("Wireless Mouse", responses.get(1).getName());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void updateProduct_Success() {
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Updated Keyboard", "New Description", new BigDecimal("109.99"), 60
        );
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.updateProduct(1L, updateRequest);

        assertNotNull(response);
        assertEquals("Updated Keyboard", sampleProduct.getName());
        assertEquals("New Description", sampleProduct.getDescription());
        assertEquals(new BigDecimal("109.99"), sampleProduct.getPrice());
        assertEquals(60, sampleProduct.getStock());
        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).save(sampleProduct);
    }

    @Test
    void updateProduct_NotFound_ThrowsResourceNotFoundException() {
        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Updated", "Desc", new BigDecimal("10.00"), 5
        );
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.updateProduct(999L, updateRequest)
        );

        assertTrue(exception.getMessage().contains("Product not found with id: 999"));
        verify(productRepository, times(1)).findById(999L);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_Success() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        doNothing().when(productRepository).delete(sampleProduct);

        assertDoesNotThrow(() -> productService.deleteProduct(1L));

        verify(productRepository, times(1)).findById(1L);
        verify(productRepository, times(1)).delete(sampleProduct);
    }

    @Test
    void deleteProduct_NotFound_ThrowsResourceNotFoundException() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> productService.deleteProduct(999L)
        );

        assertTrue(exception.getMessage().contains("Product not found with id: 999"));
        verify(productRepository, times(1)).findById(999L);
        verify(productRepository, never()).delete(any(Product.class));
    }
}
