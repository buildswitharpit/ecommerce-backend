package com.ecommerce.entity;

/**
 * Order lifecycle status. Valid manual transitions (enforced in
 * {@code OrderServiceImpl}) are: {@code PAID -> SHIPPED}, {@code SHIPPED -> DELIVERED},
 * {@code PAID -> CANCELLED} and {@code SHIPPED -> CANCELLED} (both restock),
 * {@code PENDING/PAYMENT_FAILED -> CANCELLED} (no restock, nothing was ever
 * decremented). {@code DELIVERED} is terminal -- cancelling a delivered order would
 * require a returns/refund flow, which is out of scope for this project.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    PAYMENT_FAILED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
