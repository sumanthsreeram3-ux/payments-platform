package com.sumanth.payments.payments_platform.subscription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    public record CreateSubscriptionRequest(@NotNull UUID customerId, @NotNull UUID planId,
                                            boolean trial) {}

    public record SubscriptionResponse(UUID id, UUID customerId, UUID planId,
                                       SubscriptionStatus status, Instant currentPeriodStart,
                                       Instant currentPeriodEnd, Instant canceledAt) {
        static SubscriptionResponse from(Subscription s) {
            return new SubscriptionResponse(s.getId(), s.getCustomerId(), s.getPlanId(), s.getStatus(),
                    s.getCurrentPeriodStart(), s.getCurrentPeriodEnd(), s.getCanceledAt());
        }
    }

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@Valid @RequestBody CreateSubscriptionRequest req) {
        return SubscriptionResponse.from(service.create(req.customerId(), req.planId(), req.trial()));
    }

    @GetMapping("/{id}")
    public SubscriptionResponse get(@PathVariable UUID id) {
        return SubscriptionResponse.from(service.get(id));
    }

    @GetMapping
    public List<SubscriptionResponse> byCustomer(@RequestParam UUID customerId) {
        return service.forCustomer(customerId).stream().map(SubscriptionResponse::from).toList();
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionResponse cancel(@PathVariable UUID id) {
        return SubscriptionResponse.from(service.cancel(id));
    }
}