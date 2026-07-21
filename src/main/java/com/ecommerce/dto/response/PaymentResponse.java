package com.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "The most recent charge attempt for an order")
public record PaymentResponse(
        @Schema(example = "1") Long id,
        @Schema(description = "Which PaymentGateway implementation handled the charge", example = "MOCK") String gateway,
        @Schema(description = "Opaque reference id from the gateway", example = "mock_3f2a1c9e-...") String gatewayReferenceId,
        @Schema(example = "59.98") BigDecimal amount,
        @Schema(example = "SUCCEEDED") String status,
        @Schema(example = "2026-07-21T10:20:00") LocalDateTime createdAt
) {
}
