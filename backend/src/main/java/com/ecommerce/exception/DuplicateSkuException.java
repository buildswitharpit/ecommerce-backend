package com.ecommerce.exception;

public class DuplicateSkuException extends ConflictException {

    public DuplicateSkuException(String sku) {
        super("A product with sku '" + sku + "' already exists");
    }
}
