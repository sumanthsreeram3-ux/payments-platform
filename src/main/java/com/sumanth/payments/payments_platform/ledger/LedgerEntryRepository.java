package com.sumanth.payments.payments_platform.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {}