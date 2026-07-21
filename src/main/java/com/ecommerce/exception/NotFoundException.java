package com.ecommerce.exception;

/**
 * A requested resource does not exist, or (for orders) exists but the caller isn't
 * entitled to see it -- in the latter case 404 is used deliberately instead of 403 to
 * avoid revealing that the resource exists at all.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
