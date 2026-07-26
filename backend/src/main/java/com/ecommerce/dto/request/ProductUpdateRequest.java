package com.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Fields to replace on an existing product (admin-only). Does not touch stockQuantity -- " +
        "use PATCH /{id}/stock for inventory adjustments -- or active -- use DELETE /{id} for soft delete.")
public class ProductUpdateRequest {

    @Schema(example = "SKU-WIDGET-001")
    @NotBlank(message = "sku is required")
    private String sku;

    @Schema(example = "Deluxe Widget v2")
    @NotBlank(message = "name is required")
    private String name;

    @Schema(example = "A widget, but even more deluxe.")
    private String description;

    @Schema(example = "34.99")
    @NotNull(message = "price is required")
    @DecimalMin(value = "0.01", message = "price must be greater than 0")
    private BigDecimal price;

    @Schema(example = "Widgets")
    private String category;

    public ProductUpdateRequest() {
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
