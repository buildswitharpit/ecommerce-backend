package com.ecommerce.exception;

/**
 * The request conflicts with current state: insufficient stock, an optimistic-lock
 * retry needed, a duplicate email/sku, or an unsupported order status transition.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
