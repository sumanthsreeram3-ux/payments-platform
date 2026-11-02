package com.sumanth.payments.payments_platform.it;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentIdempotencyIT extends AbstractIntegrationTest {

    @Test
    void fiftyConcurrentRequestsWithSameKeyChargeExactlyOnce() throws Exception {
        UUID customerId = insertCustomer();
        String key = "race-" + UUID.randomUUID();
        String body = paymentJson(customerId, 10_000, false);

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                go.await();                       // release all threads at the same moment
                return postPayment(key, body).statusCode();
            }));
        }
        go.countDown();

        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : futures) {
            codes.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertEquals(1, codes.stream().filter(c -> c == 201).count(),
                "exactly one request should create the payment");
        assertEquals(threads - 1, codes.stream().filter(c -> c == 200).count(),
                "every other request should be a replay");

        assertEquals(1, count("SELECT count(*) FROM payments WHERE customer_id = ?", customerId));
        assertEquals(2, count("""
                SELECT count(*) FROM ledger_entries WHERE transaction_id =
                    (SELECT ledger_transaction_id FROM payments WHERE customer_id = ?)
                """, customerId));
        assertEquals(1, count("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id =
                    (SELECT id FROM payments WHERE customer_id = ?)
                """, customerId));
        assertTrue(ledgerBalanced());
    }

    @Test
    void reusingAKeyWithDifferentParametersIsRejected() throws Exception {
        UUID customerId = insertCustomer();
        String key = "key-" + UUID.randomUUID();

        assertEquals(201, postPayment(key, paymentJson(customerId, 5_000, false)).statusCode());
        assertEquals(422, postPayment(key, paymentJson(customerId, 6_000, false)).statusCode());

        assertEquals(1, count("SELECT count(*) FROM payments WHERE customer_id = ?", customerId));
    }

    @Test
    void failedPaymentIsRecordedButNeverTouchesTheLedger() throws Exception {
        UUID customerId = insertCustomer();

        var response = postPayment("fail-" + UUID.randomUUID(), paymentJson(customerId, 7_000, true));

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("FAILED"));
        assertEquals(1, count("""
                SELECT count(*) FROM payments
                WHERE customer_id = ? AND status = 'FAILED' AND ledger_transaction_id IS NULL
                """, customerId));
        assertTrue(ledgerBalanced());
    }
}