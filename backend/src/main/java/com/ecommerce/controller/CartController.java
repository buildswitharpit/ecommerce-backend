package com.ecommerce.controller;

import com.ecommerce.dto.request.CartItemAddRequest;
import com.ecommerce.dto.request.CartItemUpdateRequest;
import com.ecommerce.dto.response.CartResponse;
import com.ecommerce.dto.response.ErrorResponse;
import com.ecommerce.security.UserPrincipal;
import com.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own shopping cart. Every operation is scoped to the JWT principal --
 * there is no way to view or modify another user's cart through this API.
 */
@RestController
@RequestMapping("/api/cart")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "The authenticated caller's shopping cart (get-or-create on first access)")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(
            summary = "Get (or lazily create) the caller's cart",
            description = "Requires a bearer token. A user's cart is created automatically the first time it's accessed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's cart",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.getCart(principal.id()));
    }

    @PostMapping("/items")
    @Operation(
            summary = "Add a product to the cart",
            description = "Requires a bearer token. If the product is already in the cart, quantity is added to "
                    + "(not replaced by) the existing line. Returns 404 if the product doesn't exist or is "
                    + "soft-deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated cart",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No active product exists with the given id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal UserPrincipal principal,
                                                 @Valid @RequestBody CartItemAddRequest request) {
        return ResponseEntity.ok(cartService.addItem(principal.id(), request));
    }

    @PutMapping("/items/{productId}")
    @Operation(
            summary = "Set the quantity of an existing cart line item",
            description = "Requires a bearer token. Returns 404 if the product isn't currently in the cart."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated cart",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product isn't currently in the caller's cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Product ID", example = "1", required = true) @PathVariable Long productId,
            @Valid @RequestBody CartItemUpdateRequest request) {
        return ResponseEntity.ok(cartService.updateItem(principal.id(), productId, request));
    }

    @DeleteMapping("/items/{productId}")
    @Operation(
            summary = "Remove a single line item from the cart",
            description = "Requires a bearer token. Returns 404 if the product isn't currently in the cart."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated cart",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Product isn't currently in the caller's cart",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Product ID", example = "1", required = true) @PathVariable Long productId) {
        return ResponseEntity.ok(cartService.removeItem(principal.id(), productId));
    }

    @DeleteMapping
    @Operation(
            summary = "Clear the entire cart",
            description = "Requires a bearer token. Removes every line item; leaves the (now-empty) cart itself in place."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empty cart",
                    content = @Content(schema = @Schema(implementation = CartResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CartResponse> clear(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(cartService.clear(principal.id()));
    }
}
