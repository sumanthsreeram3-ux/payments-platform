package com.sumanth.payments.payments_platform.subscription;

import com.sumanth.payments.payments_platform.common.ConflictException;
import com.sumanth.payments.payments_platform.common.NotFoundException;
import com.sumanth.payments.payments_platform.customer.CustomerRepository;
import com.sumanth.payments.payments_platform.plan.BillingInterval;
import com.sumanth.payments.payments_platform.plan.Plan;
import com.sumanth.payments.payments_platform.plan.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subs;
    private final CustomerRepository customers;
    private final PlanRepository plans;

    public SubscriptionService(SubscriptionRepository subs, CustomerRepository customers,
                               PlanRepository plans) {
        this.subs = subs;
        this.customers = customers;
        this.plans = plans;
    }

    @Transactional
    public Subscription create(UUID customerId, UUID planId, boolean trial) {
        if (!customers.existsById(customerId)) throw new NotFoundException("Customer not found");
        Plan plan = plans.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
        if (!plan.isActive()) throw new ConflictException("Plan is not active");

        boolean duplicate = subs.existsByCustomerIdAndPlanIdAndStatusIn(customerId, planId,
                List.of(SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE));
        if (duplicate) throw new ConflictException("Customer already has a live subscription to this plan");

        Instant start = Instant.now();
        Instant end = trial
                ? start.plusSeconds(14L * 24 * 3600)
                : nextPeriodEnd(start, plan.getBillingInterval());
        SubscriptionStatus status = trial ? SubscriptionStatus.TRIALING : SubscriptionStatus.ACTIVE;
        return subs.save(new Subscription(customerId, planId, status, start, end));
    }

    @Transactional
    public Subscription cancel(UUID id) {
        Subscription s = subs.findById(id).orElseThrow(() -> new NotFoundException("Subscription not found"));
        s.transitionTo(SubscriptionStatus.CANCELED);
        return s;
    }

    @Transactional(readOnly = true)
    public Subscription get(UUID id) {
        return subs.findById(id).orElseThrow(() -> new NotFoundException("Subscription not found"));
    }

    @Transactional(readOnly = true)
    public List<Subscription> forCustomer(UUID customerId) {
        return subs.findByCustomerId(customerId);
    }

    private Instant nextPeriodEnd(Instant from, BillingInterval interval) {
        ZonedDateTime z = from.atZone(ZoneOffset.UTC);
        return (interval == BillingInterval.MONTHLY ? z.plusMonths(1) : z.plusYears(1)).toInstant();
    }
}