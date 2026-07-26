package com.ecommerce.entity;

/**
 * Application-level user role. {@code CUSTOMER} is the default assigned at
 * registration; {@code ADMIN} unlocks catalog/order management endpoints. A caller can
 * never self-assign {@code ADMIN} through the public registration endpoint.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
