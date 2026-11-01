package com.sumanth.payments.payments_platform.ledger;

import com.sumanth.payments.payments_platform.ledger.LedgerService.EntryRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerServiceTest {

    @Test
    void balancedTransactionPasses() {
        assertDoesNotThrow(() -> LedgerService.validateBalanced(List.of(
                new EntryRequest("CASH", Direction.DEBIT, 500),
                new EntryRequest("REVENUE", Direction.CREDIT, 500))));
    }

    @Test
    void unbalancedTransactionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LedgerService.validateBalanced(List.of(
                new EntryRequest("CASH", Direction.DEBIT, 500),
                new EntryRequest("REVENUE", Direction.CREDIT, 400))));
    }

    @Test
    void singleEntryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> LedgerService.validateBalanced(List.of(
                new EntryRequest("CASH", Direction.DEBIT, 500))));
    }
}