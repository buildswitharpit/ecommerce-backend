package com.ecommerce.exception;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
