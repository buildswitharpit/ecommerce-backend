package com.ecommerce.exception;

public class InvalidStatusTransitionException extends ConflictException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
