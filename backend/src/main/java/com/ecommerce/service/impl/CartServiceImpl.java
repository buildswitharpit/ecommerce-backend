package com.ecommerce.service.impl;

import com.ecommerce.dto.request.CartItemAddRequest;
import com.ecommerce.dto.request.CartItemUpdateRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.User;
import com.ecommerce.exception.NotFoundException;
import com.ecommerce.mapper.CartMapper;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartMapper cartMapper;

    public CartServiceImpl(CartRepository cartRepository, CartItemRepository cartItemRepository,
                            ProductRepository productRepository, UserRepository userRepository,
                            CartMapper cartMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartMapper = cartMapper;
    }

    @Override
    @Transactional
    public CartResponse getCart(Long userId) {
        return cartMapper.toResponse(getOrCreateCartEntity(userId));
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, CartItemAddRequest request) {
        Cart cart = getOrCreateCartEntity(userId);
        Product product = productRepository.findById(request.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("No product found with id: " + request.getProductId()));

        CartItem existing = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.getQuantity());
            cartItemRepository.save(existing);
        } else {
            CartItem item = new CartItem(cart, product, request.getQuantity());
            cart.getItems().add(item);
            cartItemRepository.save(item);
        }

        return cartMapper.toResponse(getOrCreateCartEntity(userId));
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long productId, CartItemUpdateRequest request) {
        Cart cart = getOrCreateCartEntity(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new NotFoundException("No cart item found for product id: " + productId));
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return cartMapper.toResponse(getOrCreateCartEntity(userId));
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long productId) {
        Cart cart = getOrCreateCartEntity(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new NotFoundException("No cart item found for product id: " + productId));
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        return cartMapper.toResponse(getOrCreateCartEntity(userId));
    }

    @Override
    @Transactional
    public CartResponse clear(Long userId) {
        Cart cart = getOrCreateCartEntity(userId);
        clearCartEntity(cart);
        return cartMapper.toResponse(cart);
    }

    @Override
    @Transactional
    public Cart getOrCreateCartEntity(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("No user found with id: " + userId));
            return cartRepository.save(new Cart(user));
        });
    }

    @Override
    @Transactional
    public void clearCartEntity(Cart cart) {
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();
    }
}
