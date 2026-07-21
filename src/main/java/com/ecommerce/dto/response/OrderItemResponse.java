package com.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A line item on an order, frozen at checkout time")
public record OrderItemResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "1") Long productId,
        @Schema(description = "Product name at the time of checkout", example = "Deluxe Widget") String productName,
        @Schema(description = "Unit price at the time of checkout", example = "29.99") BigDecimal unitPrice,
        @Schema(example = "2") int quantity,
        @Schema(example = "59.98") BigDecimal lineTotal
) {
}
