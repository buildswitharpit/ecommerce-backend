package com.ecommerce.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Issued token pair. Send accessToken as 'Authorization: Bearer <accessToken>' on protected " +
        "requests; use refreshToken with POST /api/auth/refresh once accessToken expires.")
public record AuthResponse(
        @Schema(description = "Short-lived JWT access token") String accessToken,
        @Schema(description = "Long-lived opaque refresh token, persisted server-side so it can be revoked") String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Access token lifetime in seconds", example = "900") long expiresInSeconds
) {
}
