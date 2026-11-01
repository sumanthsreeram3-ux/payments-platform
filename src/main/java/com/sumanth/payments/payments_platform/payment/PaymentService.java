package com.sumanth.payments.payments_platform.payment;

import com.sumanth.payments.payments_platform.common.ConflictException;
import com.sumanth.payments.payments_platform.common.NotFoundException;
import com.sumanth.payments.payments_platform.common.UnprocessableException;
import com.sumanth.payments.payments_platform.customer.CustomerRepository;
import com.sumanth.payments.payments_platform.ledger.Direction;
import com.sumanth.payments.payments_platform.ledger.LedgerService;
import com.sumanth.payments.payments_platform.ledger.LedgerService.EntryRequest;
import com.sumanth.payments.payments_platform.subscription.SubscriptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    public record ChargeCommand(UUID customerId, UUID subscriptionId, long amountMinor,
                                String currency, String description, boolean simulateFailure) {}

    public record ChargeResult(Payment payment, boolean replayed) {}

    private final JdbcTemplate jdbc;
    private final PaymentRepository payments;
    private final CustomerRepository customers;
    private final SubscriptionRepository subscriptions;
    private final LedgerService ledger;

    public PaymentService(JdbcTemplate jdbc, PaymentRepository payments, CustomerRepository customers,
                          SubscriptionRepository subscriptions, LedgerService ledger) {
        this.jdbc = jdbc;
        this.payments = payments;
        this.customers = customers;
        this.subscriptions = subscriptions;
        this.ledger = ledger;
    }

    @Transactional
    public ChargeResult charge(String idempotencyKey, ChargeCommand cmd) {
        String hash = sha256(cmd.customerId() + "|" + cmd.subscriptionId() + "|" + cmd.amountMinor()
                + "|" + cmd.currency() + "|" + cmd.description() + "|" + cmd.simulateFailure());

        // Claim the key. Concurrent requests with the same key block here until
        // the first transaction commits or rolls back.
        int claimed = jdbc.update(
                "INSERT INTO idempotency_keys (key, request_hash) VALUES (?, ?) ON CONFLICT (key) DO NOTHING",
                idempotencyKey, hash);
        if (claimed == 0) {
            return replay(idempotencyKey, hash);
        }

        if (!customers.existsById(cmd.customerId())) throw new NotFoundException("Customer not found");
        if (cmd.subscriptionId() != null && !subscriptions.existsById(cmd.subscriptionId())) {
            throw new NotFoundException("Subscription not found");
        }

        PaymentStatus status = cmd.simulateFailure() ? PaymentStatus.FAILED : PaymentStatus.SUCCEEDED;
        UUID ledgerTxId = null;
        if (status == PaymentStatus.SUCCEEDED) {
            ledgerTxId = ledger.post("Payment from customer " + cmd.customerId(), cmd.currency(), List.of(
                    new EntryRequest("CASH", Direction.DEBIT, cmd.amountMinor()),
                    new EntryRequest("REVENUE", Direction.CREDIT, cmd.amountMinor()))).getId();
        }

        Payment payment = payments.saveAndFlush(new Payment(cmd.customerId(), cmd.subscriptionId(),
                cmd.amountMinor(), cmd.currency(), status, cmd.description(), ledgerTxId));

        jdbc.update("UPDATE idempotency_keys SET payment_id = ?, status_code = ? WHERE key = ?",
                payment.getId(), 201, idempotencyKey);
        return new ChargeResult(payment, false);
    }

    private ChargeResult replay(String key, String hash) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT request_hash, payment_id FROM idempotency_keys WHERE key = ?", key);
        if (!hash.equals(row.get("request_hash"))) {
            throw new UnprocessableException("Idempotency-Key was already used with different request parameters");
        }
        UUID paymentId = (UUID) row.get("payment_id");
        if (paymentId == null) throw new ConflictException("Original request is still in progress");
        return new ChargeResult(payments.findById(paymentId).orElseThrow(), true);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}