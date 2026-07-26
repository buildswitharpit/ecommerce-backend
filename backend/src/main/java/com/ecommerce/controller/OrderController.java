package com.ecommerce.controller;

import com.ecommerce.dto.request.CheckoutRequest;
import com.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.ecommerce.dto.response.ErrorResponse;
import com.ecommerce.dto.response.OrderResponse;
import com.ecommerce.security.UserPrincipal;
import com.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout and order lookup for the authenticated caller, plus the (admin-gated)
 * order-status transition endpoint. See {@code AdminOrderController} for the
 * cross-customer admin order listing.
 */
@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Checkout and the caller's own order history; status transitions are admin-only")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @Operation(
            summary = "Checkout the caller's cart",
            description = "Requires a bearer token for any authenticated account. Validates every cart line "
                    + "(product still active, sufficient stock), creates the order, then charges the active "
                    + "PaymentGateway. On success: stock is decremented, an InventoryTransaction audit row is "
                    + "written per product, the order is marked PAID, and the cart is cleared. On a decline "
                    + "(with the mock gateway: paymentMethodToken='tok_chargeDeclined'), the order is marked "
                    + "PAYMENT_FAILED, stock is left untouched, and the cart survives so the caller can retry. "
                    + "409 if the cart is empty or any line fails validation -- nothing is charged or created in "
                    + "that case."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order created; check the 'status' field for PAID vs PAYMENT_FAILED",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Cart is empty, a product is inactive/out of stock, or a concurrent stock change was detected (retry)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal UserPrincipal principal,
                                                    @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(orderService.checkout(principal.id(), request));
    }

    @GetMapping
    @Operation(
            summary = "List the caller's own orders",
            description = "Requires a bearer token. Paginated; standard page/size/sort query params."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of the caller's orders"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<OrderResponse>> myOrders(@AuthenticationPrincipal UserPrincipal principal,
                                                          @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(orderService.findMyOrders(principal.id(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one order by id",
            description = "Requires a bearer token. Returns 404 if the order doesn't exist, or belongs to a "
                    + "different customer and the caller isn't ADMIN (deliberately 404, not 403, so the endpoint "
                    + "never reveals whether an order id belonging to someone else exists)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such order, or it isn't visible to the caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> getById(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Order ID", example = "1", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(orderService.findById(id, principal));
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Transition an order's status (admin-only)",
            description = "Requires a bearer token for an ADMIN account. Valid transitions: PAID->SHIPPED, "
                    + "SHIPPED->DELIVERED, PAID->CANCELLED and SHIPPED->CANCELLED (both restock -- stockQuantity "
                    + "is incremented back and an ORDER_CANCELLED InventoryTransaction is written per line item), "
                    + "and PENDING/PAYMENT_FAILED->CANCELLED (no restock, since stock was never decremented for "
                    + "those). Cancelling a DELIVERED order is rejected with 409 -- that would require a "
                    + "returns/refund flow, out of scope for this project."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No order exists with the given id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "The requested transition isn't allowed from the order's current status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<OrderResponse> updateStatus(
            @Parameter(description = "Order ID", example = "1", required = true) @PathVariable Long id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request));
    }
}
