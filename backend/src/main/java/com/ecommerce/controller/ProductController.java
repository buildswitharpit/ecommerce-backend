package com.ecommerce.controller;

import com.ecommerce.dto.request.ProductCreateRequest;
import com.ecommerce.dto.request.ProductUpdateRequest;
import com.ecommerce.dto.request.StockAdjustRequest;
import com.ecommerce.dto.response.ErrorResponse;
import com.ecommerce.dto.response.ProductResponse;
import com.ecommerce.security.UserPrincipal;
import com.ecommerce.service.ProductService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product catalog: public browsing/search, admin-only writes and stock adjustment.
 */
@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Browse/search the catalog (public), manage it and adjust stock (admin-only)")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(
            summary = "List / search products",
            description = "Public. Returns a paginated list of products, filterable by exact-match 'category' "
                    + "and a case-insensitive substring 'search' against the product name. Anonymous and CUSTOMER "
                    + "callers only ever see active=true products; an authenticated ADMIN caller also sees "
                    + "soft-deleted (active=false) products."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of products returned successfully (may be empty)"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Page<ProductResponse>> list(
            @Parameter(description = "Exact-match category filter (case-insensitive)", example = "Widgets")
            @RequestParam(required = false) String category,
            @Parameter(description = "Keyword matched against product name (case-insensitive substring)", example = "widget")
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal principal,
            @ParameterObject Pageable pageable) {
        boolean isAdmin = principal != null && principal.role() == com.ecommerce.entity.Role.ADMIN;
        return ResponseEntity.ok(productService.findAll(category, search, isAdmin, pageable));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one product by id",
            description = "Public. Returns 404 if the product doesn't exist, and also 404 for a soft-deleted "
                    + "(active=false) product unless the caller is authenticated as ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "No product exists with the given id (or it's soft-deleted and caller isn't ADMIN)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> getById(
            @Parameter(description = "Product ID", example = "1", required = true) @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal != null && principal.role() == com.ecommerce.entity.Role.ADMIN;
        return ResponseEntity.ok(productService.findById(id, isAdmin));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create a product (admin-only)",
            description = "Requires a bearer token for an ADMIN account. Returns 409 if the sku is already in use."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "sku already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Replace a product's editable fields (admin-only)",
            description = "Requires a bearer token for an ADMIN account. Does not touch stockQuantity (use PATCH "
                    + "/{id}/stock) or active (use DELETE /{id})."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product updated",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No product exists with the given id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "New sku already used by another product",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> update(
            @Parameter(description = "Product ID", example = "1", required = true) @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Soft-delete a product (admin-only)",
            description = "Requires a bearer token for an ADMIN account. Sets active=false rather than removing "
                    + "the row, since past OrderItem rows reference this product for traceability and must never "
                    + "break."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product soft-deleted"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No product exists with the given id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Product ID", example = "1", required = true) @PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Manually adjust stock (admin-only)",
            description = "Requires a bearer token for an ADMIN account. Applies a signed quantityChange to "
                    + "stockQuantity (e.g. +50 to restock, -5 to correct a miscount) and writes one "
                    + "InventoryTransaction audit row. Rejected with 409 if the adjustment would drive stock "
                    + "negative, or if a concurrent update changed stock first (optimistic-lock conflict)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock adjusted",
                    content = @Content(schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request body failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid access token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is authenticated but not ADMIN",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No product exists with the given id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Adjustment would result in negative stock, or a concurrent stock change was detected",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ProductResponse> adjustStock(
            @Parameter(description = "Product ID", example = "1", required = true) @PathVariable Long id,
            @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(productService.adjustStock(id, request));
    }
}
