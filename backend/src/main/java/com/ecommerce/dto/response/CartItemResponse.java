package com.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A single line item in a cart")
public record CartItemResponse(
        @Schema(example = "1") Long id,
        @Schema(description = "The product's current catalog state") ProductResponse product,
        @Schema(example = "2") int quantity,
        @Schema(description = "product.price * quantity, at current catalog price", example = "59.98") BigDecimal lineTotal
) {
}
