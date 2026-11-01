package com.sumanth.payments.payments_platform.ledger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {

    public record EntryRequest(String accountName, Direction direction, long amountMinor) {}

    private final AccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;

    public LedgerService(AccountRepository accounts, LedgerTransactionRepository transactions,
                         LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
    }

    // MANDATORY: a ledger write must always join the caller's transaction,
    // so money movement and business state commit or roll back together.
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction post(String description, String currency, List<EntryRequest> lines) {
        validateBalanced(lines);
        LedgerTransaction tx = transactions.saveAndFlush(new LedgerTransaction(description));
        for (EntryRequest line : lines) {
            Account account = accounts.findByName(line.accountName())
                    .orElseThrow(() -> new IllegalStateException("Unknown account " + line.accountName()));
            entries.save(new LedgerEntry(tx.getId(), account.getId(), line.direction(),
                    line.amountMinor(), currency));
        }
        entries.flush();
        return tx;
    }

    static void validateBalanced(List<EntryRequest> lines) {
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A ledger transaction needs at least two entries");
        }
        long debits = 0, credits = 0;
        for (EntryRequest l : lines) {
            if (l.amountMinor() <= 0) throw new IllegalArgumentException("Entry amounts must be positive");
            if (l.direction() == Direction.DEBIT) debits += l.amountMinor();
            else credits += l.amountMinor();
        }
        if (debits != credits) {
            throw new IllegalArgumentException("Unbalanced transaction: debits=" + debits + ", credits=" + credits);
        }
    }
}