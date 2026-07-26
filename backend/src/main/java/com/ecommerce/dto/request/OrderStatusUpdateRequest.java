package com.ecommerce.dto.request;

import com.ecommerce.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New status to transition an order to (admin-only). See OrderStatus for the allowed " +
        "transition rules -- an unsupported transition (e.g. DELIVERED -> CANCELLED) is rejected with 409.")
public class OrderStatusUpdateRequest {

    @Schema(example = "SHIPPED")
    @NotNull(message = "status is required")
    private OrderStatus status;

    public OrderStatusUpdateRequest() {
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
