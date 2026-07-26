package com.ecommerce.dto.response;

import com.ecommerce.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "A user account. Never includes the password hash.")
public record UserResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "jane.doe@example.com") String email,
        @Schema(example = "Jane Doe") String fullName,
        @Schema(example = "CUSTOMER") Role role,
        @Schema(example = "2026-07-21T10:15:30") LocalDateTime createdAt
) {
}
