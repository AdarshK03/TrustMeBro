package com.platform.productservice.service;

import com.platform.productservice.dto.ProductCreateRequest;
import com.platform.productservice.dto.ProductResponse;
import com.platform.productservice.dto.ProductUpdateRequest;

import java.util.List;

public interface ProductService {
    ProductResponse createProduct(ProductCreateRequest request);
    ProductResponse getProductById(Long id);
    List<ProductResponse> getAllProducts();
    ProductResponse updateProduct(Long id, ProductUpdateRequest request);
    void deleteProduct(Long id);
}
