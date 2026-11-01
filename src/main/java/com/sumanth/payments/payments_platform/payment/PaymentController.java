package com.sumanth.payments.payments_platform.payment;

import com.sumanth.payments.payments_platform.payment.PaymentService.ChargeCommand;
import com.sumanth.payments.payments_platform.payment.PaymentService.ChargeResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    public record CreatePaymentRequest(
            @NotNull UUID customerId,
            UUID subscriptionId,
            @Positive long amountMinor,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @Size(max = 255) String description,
            Boolean simulateFailure) {}

    public record PaymentResponse(UUID id, UUID customerId, UUID subscriptionId, long amountMinor,
                                  String currency, PaymentStatus status, String description,
                                  Instant createdAt) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getCustomerId(), p.getSubscriptionId(),
                    p.getAmountMinor(), p.getCurrency(), p.getStatus(), p.getDescription(),
                    p.getCreatedAt());
        }
    }

    private final PaymentService service;

    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody CreatePaymentRequest req) {

        if (key.isBlank() || key.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 1 to 255 characters");
        }

        ChargeResult result = service.charge(key, new ChargeCommand(
                req.customerId(),
                req.subscriptionId(),
                req.amountMinor(),
                req.currency(),
                req.description(),
                Boolean.TRUE.equals(req.simulateFailure())));

        PaymentResponse body = PaymentResponse.from(result.payment());
        return result.replayed()
                ? ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body)
                : ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}