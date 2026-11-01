package com.sumanth.payments.payments_platform.customer;

import com.sumanth.payments.payments_platform.common.ConflictException;
import com.sumanth.payments.payments_platform.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    public record CreateCustomerRequest(@NotBlank @Email String email, @NotBlank String name) {}
    public record CustomerResponse(UUID id, String email, String name, Instant createdAt) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getEmail(), c.getName(), c.getCreatedAt());
        }
    }

    private final CustomerRepository repo;

    public CustomerController(CustomerRepository repo) { this.repo = repo; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest req) {
        if (repo.existsByEmail(req.email())) {
            throw new ConflictException("Customer with this email already exists");
        }
        return CustomerResponse.from(repo.save(new Customer(req.email(), req.name())));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return repo.findById(id).map(CustomerResponse::from)
                .orElseThrow(() -> new NotFoundException("Customer not found"));
    }
}