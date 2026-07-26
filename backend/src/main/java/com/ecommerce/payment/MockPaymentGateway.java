package com.ecommerce.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Default {@link PaymentGateway} implementation: no external dependency, no API key,
 * nothing to configure. Simulates a realistic charge:
 *
 * <ul>
 *     <li>Any {@code paymentMethodToken} succeeds, returning a fake gateway reference
 *     id ({@code "mock_" + UUID}), <b>except</b></li>
 *     <li>the literal string {@code "tok_chargeDeclined"}, which simulates a card
 *     decline -- deliberately mirroring Stripe's own test-mode magic decline tokens
 *     (e.g. {@code tok_chargeDeclined}, {@code 4000000000000002}) so the abstraction
 *     reads naturally and a caller can exercise the decline path identically against
 *     either gateway.</li>
 * </ul>
 *
 * Active whenever {@code payment.gateway} is {@code mock} (the default -- see
 * {@code matchIfMissing = true} below) or unset.
 */
@Service
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    public static final String DECLINE_TOKEN = "tok_chargeDeclined";

    @Override
    public PaymentResult charge(BigDecimal amount, String paymentMethodToken) {
        if (DECLINE_TOKEN.equals(paymentMethodToken)) {
            log.info("Mock gateway: simulating a decline for amount {} (token={})", amount, paymentMethodToken);
            return PaymentResult.failure("MOCK", "Your card was declined (simulated by mock gateway)");
        }
        String reference = "mock_" + UUID.randomUUID();
        log.info("Mock gateway: simulating a successful charge of {} -> {}", amount, reference);
        return PaymentResult.success("MOCK", reference);
    }
}
