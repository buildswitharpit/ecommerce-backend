package com.ecommerce.controller;

import com.ecommerce.dto.response.ErrorResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.entity.OrderStatus;
import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only cross-customer order visibility. The order status transition endpoint
 * (also admin-only) lives on {@code OrderController} at
 * {@code PATCH /api/orders/{id}/status} rather than under {@code /api/admin} -- see
 * that controller's Javadoc.
 */
@RestController
@RequestMapping("/api/admin/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Admin-only cross-customer order visibility")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(
            summary = "List all orders across every customer (admin-only)",
            description = "Requires a bearer token for an ADMIN account. Paginated; optional exact-match 'status' filter."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of orders"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<OrderResponse>> listAll(
            @Parameter(description = "Filter to orders with exactly this status", example = "PAID")
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(orderService.findAllForAdmin(status, pageable));
    }
}
