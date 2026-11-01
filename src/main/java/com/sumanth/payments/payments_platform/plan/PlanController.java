package com.sumanth.payments.payments_platform.plan;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/plans")
public class PlanController {

    public record CreatePlanRequest(
            @NotBlank @Size(max = 100) String name,
            @Positive long amountMinor,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull BillingInterval billingInterval) {}

    public record PlanResponse(UUID id, String name, long amountMinor, String currency,
                               BillingInterval billingInterval, boolean active) {
        static PlanResponse from(Plan p) {
            return new PlanResponse(p.getId(), p.getName(), p.getAmountMinor(),
                    p.getCurrency(), p.getBillingInterval(), p.isActive());
        }
    }

    private final PlanRepository repo;

    public PlanController(PlanRepository repo) { this.repo = repo; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest req) {
        return PlanResponse.from(repo.save(
                new Plan(req.name(), req.amountMinor(), req.currency(), req.billingInterval())));
    }

    @GetMapping
    public List<PlanResponse> list() {
        return repo.findAll().stream().map(PlanResponse::from).toList();
    }
}