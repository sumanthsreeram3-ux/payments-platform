package com.sumanth.payments.payments_platform.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {}