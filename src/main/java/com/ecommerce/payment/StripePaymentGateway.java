package com.ecommerce.payment;

import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.param.ChargeCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Real Stripe integration, using the official {@code com.stripe:stripe-java} SDK to
 * create a genuine {@link Charge} in Stripe test mode. This class is written and wired
 * completely, but is <b>not exercised live</b> in this project's own verification run
 * (no Stripe account/credentials are available in that environment) -- it exists to
 * demonstrate a correct, real third-party payment integration, ready to activate the
 * moment real test-mode credentials are supplied.
 *
 * <p>Activated only when <b>both</b>:
 * <ul>
 *     <li>{@code payment.gateway=stripe} (env var {@code PAYMENT_GATEWAY=stripe}), and</li>
 *     <li>{@code stripe.api.key} is set to a real Stripe secret test key (env var
 *     {@code STRIPE_API_KEY=sk_test_...})</li>
 * </ul>
 * The default ({@code payment.gateway} unset or {@code mock}) always wires
 * {@link MockPaymentGateway} instead -- see its Javadoc.
 *
 * <p>{@code paymentMethodToken} is passed straight through as the Stripe {@code source}
 * token (e.g. Stripe's own test tokens such as {@code tok_visa} /
 * {@code tok_chargeDeclined}), using the classic Charges API rather than the newer
 * PaymentIntents/PaymentMethods flow, since a single-use source token is the closest
 * real-Stripe analog to this project's {@code paymentMethodToken} abstraction.
 */
@Service
@ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    @Value("${stripe.api.key:}")
    private String apiKey;

    @Value("${stripe.currency:usd}")
    private String currency;

    @PostConstruct
    void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("payment.gateway=stripe but stripe.api.key is not set -- every charge attempt will fail");
        }
        Stripe.apiKey = apiKey;
    }

    @Override
    public PaymentResult charge(BigDecimal amount, String paymentMethodToken) {
        try {
            long amountInMinorUnits = amount.setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();

            ChargeCreateParams params = ChargeCreateParams.builder()
                    .setAmount(amountInMinorUnits)
                    .setCurrency(currency)
                    .setSource(paymentMethodToken)
                    .setDescription("E-commerce backend order checkout")
                    .build();

            Charge charge = Charge.create(params);

            if (Boolean.TRUE.equals(charge.getPaid()) && "succeeded".equals(charge.getStatus())) {
                return PaymentResult.success("STRIPE", charge.getId());
            }
            String reason = charge.getFailureMessage() != null ? charge.getFailureMessage() : "Charge status: " + charge.getStatus();
            return PaymentResult.failure("STRIPE", reason);

        } catch (CardException e) {
            // Expected decline path: Stripe represents a decline as an exception carrying
            // card-decline details rather than a normal return value.
            log.info("Stripe charge declined: {}", e.getMessage());
            return PaymentResult.failure("STRIPE", e.getMessage());
        } catch (StripeException e) {
            log.error("Stripe charge attempt failed with an API/infrastructure error", e);
            return PaymentResult.failure("STRIPE", "Payment gateway error: " + e.getMessage());
        }
    }
}
