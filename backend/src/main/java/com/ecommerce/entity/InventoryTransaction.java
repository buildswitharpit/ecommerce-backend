package com.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * An immutable audit-trail row for every stock change, written alongside every
 * mutation to {@link Product#getStockQuantity()} rather than relying on the current
 * stock number alone. {@code quantityChange} is signed (negative for a checkout
 * deduction, positive for a restock or a cancellation refund).
 */
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReason reason;

    @Column(name = "reference_order_id")
    private Long referenceOrderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public InventoryTransaction() {
    }

    public InventoryTransaction(Product product, int quantityChange, InventoryReason reason, Long referenceOrderId) {
        this.product = product;
        this.quantityChange = quantityChange;
        this.reason = reason;
        this.referenceOrderId = referenceOrderId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public InventoryReason getReason() {
        return reason;
    }

    public void setReason(InventoryReason reason) {
        this.reason = reason;
    }

    public Long getReferenceOrderId() {
        return referenceOrderId;
    }

    public void setReferenceOrderId(Long referenceOrderId) {
        this.referenceOrderId = referenceOrderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
