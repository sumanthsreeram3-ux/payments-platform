package com.sumanth.payments.payments_platform.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByCustomerId(UUID customerId);
    boolean existsByCustomerIdAndPlanIdAndStatusIn(UUID customerId, UUID planId,
                                                   List<SubscriptionStatus> statuses);
}