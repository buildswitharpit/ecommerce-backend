package com.ecommerce.payment;

/**
 * The outcome of a single {@link PaymentGateway#charge} attempt.
 *
 * @param success             whether the charge succeeded
 * @param gateway             {@code "MOCK"} or {@code "STRIPE"}
 * @param gatewayReferenceId  the gateway's opaque reference for the attempt (non-null on success)
 * @param failureReason       a human-readable decline/error reason (non-null on failure)
 */
public record PaymentResult(boolean success, String gateway, String gatewayReferenceId, String failureReason) {

    public static PaymentResult success(String gateway, String gatewayReferenceId) {
        return new PaymentResult(true, gateway, gatewayReferenceId, null);
    }

    public static PaymentResult failure(String gateway, String failureReason) {
        return new PaymentResult(false, gateway, null, failureReason);
    }
}
