package com.ecommerce.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ecommerce.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "An order and its current state")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "PAID") OrderStatus status,
        @Schema(description = "Snapshot of the total at checkout time", example = "59.98") BigDecimal totalAmount,
        @Schema(example = "2026-07-21T10:20:00") LocalDateTime createdAt,
        List<OrderItemResponse> items,
        @Schema(description = "Present once a checkout attempt has been made; null for a never-checked-out order")
        PaymentResponse payment
) {
}
