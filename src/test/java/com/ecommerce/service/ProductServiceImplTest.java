package com.ecommerce.service;

import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.dto.request.ProductUpdateRequest;
import com.ecommerce.dto.request.StockAdjustRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.InventoryReason;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ConflictException;
import com.ecommerce.exception.DuplicateSkuException;
import com.ecommerce.exception.NotFoundException;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.InventoryTransactionRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    private final ProductMapper productMapper = new ProductMapper();

    private ProductServiceImpl productService;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productRepository, inventoryTransactionRepository, productMapper);
        sampleProduct = new Product("SKU-1", "Widget", "A widget", new BigDecimal("9.99"), 10, "Widgets");
        sampleProduct.setId(1L);
    }

    @Test
    void create_newSku_succeeds() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setSku("SKU-2");
        request.setName("Gadget");
        request.setPrice(new BigDecimal("19.99"));
        request.setStockQuantity(5);

        when(productRepository.existsBySku("SKU-2")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.create(request);

        assertThat(response.sku()).isEqualTo("SKU-2");
        assertThat(response.active()).isTrue();
    }

    @Test
    void create_duplicateSku_throwsConflict() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setSku("SKU-1");
        request.setName("Widget");
        request.setPrice(new BigDecimal("9.99"));
        request.setStockQuantity(10);

        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void delete_softDeletes_setsActiveFalse() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.delete(1L);

        assertThat(sampleProduct.isActive()).isFalse();
        verify(productRepository).save(sampleProduct);
    }

    @Test
    void delete_unknownId_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findById_inactiveProduct_nonAdmin_throwsNotFound() {
        sampleProduct.setActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        assertThatThrownBy(() -> productService.findById(1L, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findById_inactiveProduct_admin_stillVisible() {
        sampleProduct.setActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        ProductResponse response = productService.findById(1L, true);

        assertThat(response.active()).isFalse();
    }

    @Test
    void adjustStock_restock_increasesQuantity_andWritesAuditRow() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustRequest request = new StockAdjustRequest();
        request.setQuantityChange(50);
        request.setReason(InventoryReason.RESTOCK);

        ProductResponse response = productService.adjustStock(1L, request);

        assertThat(response.stockQuantity()).isEqualTo(60);
        verify(inventoryTransactionRepository).save(any());
    }

    @Test
    void adjustStock_wouldGoNegative_throwsConflict_noAuditRowWritten() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));

        StockAdjustRequest request = new StockAdjustRequest();
        request.setQuantityChange(-100);
        request.setReason(InventoryReason.RESTOCK);

        assertThatThrownBy(() -> productService.adjustStock(1L, request))
                .isInstanceOf(ConflictException.class);

        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    void adjustStock_optimisticLockConflict_translatesTo409ConflictException() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.saveAndFlush(any(Product.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        StockAdjustRequest request = new StockAdjustRequest();
        request.setQuantityChange(5);
        request.setReason(InventoryReason.RESTOCK);

        assertThatThrownBy(() -> productService.adjustStock(1L, request))
                .isInstanceOf(ConflictException.class);

        verify(inventoryTransactionRepository, never()).save(any());
    }
}
