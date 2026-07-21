package com.ecommerce.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Checkout the caller's current cart. paymentMethodToken is handed as-is to the active " +
        "PaymentGateway -- with the default mock gateway, any value succeeds except the literal string " +
        "'tok_chargeDeclined', which simulates a card decline (mirrors Stripe's own test-mode magic tokens).")
public class CheckoutRequest {

    @Schema(description = "Opaque payment method token. Use 'tok_chargeDeclined' to simulate a decline against " +
            "the mock gateway.", example = "tok_visa")
    @NotBlank(message = "paymentMethodToken is required")
    private String paymentMethodToken;

    public CheckoutRequest() {
    }

    public String getPaymentMethodToken() {
        return paymentMethodToken;
    }

    public void setPaymentMethodToken(String paymentMethodToken) {
        this.paymentMethodToken = paymentMethodToken;
    }
}
