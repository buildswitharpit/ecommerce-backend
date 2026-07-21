package com.ecommerce.dto.request;

import com.ecommerce.entity.InventoryReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Manual inventory adjustment (admin-only). Writes one InventoryTransaction audit row.")
public class StockAdjustRequest {

    @Schema(description = "Signed change to apply to stockQuantity, e.g. 50 to restock or -5 to correct a miscount",
            example = "50")
    @NotNull(message = "quantityChange is required")
    private Integer quantityChange;

    @Schema(description = "Reason for the adjustment", example = "RESTOCK")
    @NotNull(message = "reason is required")
    private InventoryReason reason;

    public StockAdjustRequest() {
    }

    public Integer getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(Integer quantityChange) {
        this.quantityChange = quantityChange;
    }

    public InventoryReason getReason() {
        return reason;
    }

    public void setReason(InventoryReason reason) {
        this.reason = reason;
    }
}
