package com.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A catalog product")
public record ProductResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "SKU-WIDGET-001") String sku,
        @Schema(example = "Deluxe Widget") String name,
        @Schema(example = "A widget, but deluxe.") String description,
        @Schema(example = "29.99") BigDecimal price,
        @Schema(example = "100") int stockQuantity,
        @Schema(example = "Widgets") String category,
        @Schema(description = "false means soft-deleted -- hidden from non-admin catalog browsing", example = "true")
        boolean active
) {
}
