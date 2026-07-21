package com.ecommerce.entity;

/**
 * Why an {@link InventoryTransaction} happened: a checkout consumed stock, a cancelled
 * order returned stock, or an admin manually adjusted stock (restock or correction).
 */
public enum InventoryReason {
    ORDER_PLACED,
    ORDER_CANCELLED,
    RESTOCK
}
