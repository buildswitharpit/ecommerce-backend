package com.ecommerce.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Structured error body returned for every failed request")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(description = "Time the error occurred, in server local time", example = "2026-07-21T10:15:30")
        LocalDateTime timestamp,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "Short HTTP reason phrase for the status code", example = "Not Found")
        String error,

        @Schema(description = "Human-readable explanation of what went wrong", example = "No product found with id: 42")
        String message,

        @Schema(description = "Request path that produced the error", example = "/api/products/42")
        String path,

        @Schema(description = "Field-level validation errors, present only for 400 responses caused by request body validation failures")
        List<FieldError> fieldErrors
) {

    @Schema(description = "A single field-level validation failure")
    public record FieldError(
            @Schema(description = "Name of the request field that failed validation", example = "email")
            String field,
            @Schema(description = "Reason the field failed validation", example = "must be a well-formed email address")
            String message
    ) {
    }
}
