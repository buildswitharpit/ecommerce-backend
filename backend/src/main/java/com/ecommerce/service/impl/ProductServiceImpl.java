package com.ecommerce.service.impl;

import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.dto.request.ProductUpdateRequest;
import com.ecommerce.dto.request.StockAdjustRequest;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.entity.InventoryTransaction;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ConflictException;
import com.ecommerce.exception.DuplicateSkuException;
import com.ecommerce.exception.NotFoundException;
import com.ecommerce.mapper.ProductMapper;
import com.ecommerce.repository.InventoryTransactionRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.spec.ProductSpecifications;
import com.ecommerce.service.ProductService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository,
                               InventoryTransactionRepository inventoryTransactionRepository,
                               ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.productMapper = productMapper;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }
        Product product = new Product(request.getSku(), request.getName(), request.getDescription(),
                request.getPrice(), request.getStockQuantity(), request.getCategory());
        product = productRepository.save(product);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = findEntityById(id);

        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());

        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findEntityById(id);
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id, boolean callerIsAdmin) {
        Product product = findEntityById(id);
        if (!product.isActive() && !callerIsAdmin) {
            throw new NotFoundException("No product found with id: " + id);
        }
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(String category, String search, boolean callerIsAdmin, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.hasCategory(category),
                ProductSpecifications.matchesSearch(search));
        if (!callerIsAdmin) {
            spec = spec.and(ProductSpecifications.isActive(true));
        }
        return productRepository.findAll(spec, pageable).map(productMapper::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse adjustStock(Long id, StockAdjustRequest request) {
        Product product = findEntityById(id);

        int newQuantity = product.getStockQuantity() + request.getQuantityChange();
        if (newQuantity < 0) {
            throw new ConflictException("Stock adjustment would result in negative stock for product '"
                    + product.getName() + "' (current: " + product.getStockQuantity()
                    + ", change: " + request.getQuantityChange() + ")");
        }
        product.setStockQuantity(newQuantity);

        try {
            product = productRepository.saveAndFlush(product);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException("Stock for product '" + product.getName()
                    + "' changed concurrently, please retry");
        }

        inventoryTransactionRepository.save(new InventoryTransaction(product, request.getQuantityChange(),
                request.getReason(), null));

        return productMapper.toResponse(product);
    }

    private Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No product found with id: " + id));
    }
}
