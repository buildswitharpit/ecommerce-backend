package com.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "The caller's shopping cart")
public record CartResponse(
        @Schema(example = "1") Long id,
        List<CartItemResponse> items,
        @Schema(description = "Sum of every line item's lineTotal, at current catalog prices", example = "59.98")
        BigDecimal totalAmount
) {
}
