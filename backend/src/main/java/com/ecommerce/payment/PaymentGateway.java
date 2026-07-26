package com.ecommerce.payment;

import java.math.BigDecimal;

/**
 * Abstraction over "charge a customer for an order total". Exactly one implementation
 * is active at a time, selected by the {@code payment.gateway} property
 * ({@link MockPaymentGateway}, the default, or {@link StripePaymentGateway}) -- see
 * each implementation's Javadoc and the project README for how to switch between them.
 * {@code OrderServiceImpl} depends only on this interface, never on a concrete
 * gateway, so the checkout flow is identical regardless of which one is wired in.
 */
public interface PaymentGateway {

    /**
     * Attempt to charge {@code amount} against {@code paymentMethodToken}. Never
     * throws for an ordinary decline -- a decline is a normal, expected outcome
     * represented by {@link PaymentResult#success()} being {@code false}. May throw an
     * unchecked exception only for a genuine infrastructure failure (e.g. the gateway
     * is unreachable), which the caller treats as no different from any other 500.
     */
    PaymentResult charge(BigDecimal amount, String paymentMethodToken);
}
