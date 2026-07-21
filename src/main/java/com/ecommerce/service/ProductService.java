package com.ecommerce.service;

import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.dto.request.ProductUpdateRequest;
import com.ecommerce.dto.request.StockAdjustRequest;
import com.ecommerce.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductResponse create(ProductCreateRequest request);

    ProductResponse update(Long id, ProductUpdateRequest request);

    void delete(Long id);

    ProductResponse findById(Long id, boolean callerIsAdmin);

    Page<ProductResponse> findAll(String category, String search, boolean callerIsAdmin, Pageable pageable);

    ProductResponse adjustStock(Long id, StockAdjustRequest request);
}
