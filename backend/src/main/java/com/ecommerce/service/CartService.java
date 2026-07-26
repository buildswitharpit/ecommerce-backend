package com.ecommerce.service;

import com.ecommerce.dto.request.CartItemAddRequest;
import com.ecommerce.dto.request.CartItemUpdateRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.entity.Cart;

public interface CartService {

    CartResponse getCart(Long userId);

    CartResponse addItem(Long userId, CartItemAddRequest request);

    CartResponse updateItem(Long userId, Long productId, CartItemUpdateRequest request);

    CartResponse removeItem(Long userId, Long productId);

    CartResponse clear(Long userId);

    /**
     * Entity-level accessor used by {@code OrderServiceImpl} during checkout, which
     * needs the live {@link Cart} (and its items' {@code Product} entities) rather
     * than a read-only DTO.
     */
    Cart getOrCreateCartEntity(Long userId);

    /**
     * Removes every item from the given cart. Used by {@code OrderServiceImpl} once a
     * checkout's payment has succeeded.
     */
    void clearCartEntity(Cart cart);
}
