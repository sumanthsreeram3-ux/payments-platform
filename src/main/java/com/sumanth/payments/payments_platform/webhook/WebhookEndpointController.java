package com.sumanth.payments.payments_platform.webhook;

import com.sumanth.payments.payments_platform.webhook.WebhookService.DeliveryView;
import com.sumanth.payments.payments_platform.webhook.WebhookService.EndpointCreated;
import com.sumanth.payments.payments_platform.webhook.WebhookService.EndpointView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/webhook-endpoints")
public class WebhookEndpointController {

    public record CreateEndpointRequest(
            @NotBlank @Size(max = 2048) @Pattern(regexp = "^https?://.+", message = "must be an http(s) URL") String url,
            List<@NotBlank @Pattern(regexp = "[a-z_.]+") String> eventTypes) {}

    private final WebhookService webhooks;

    public WebhookEndpointController(WebhookService webhooks) { this.webhooks = webhooks; }

    /** The secret is returned once, at creation. Receivers use it to verify signatures. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EndpointCreated create(@Valid @RequestBody CreateEndpointRequest req) {
        return webhooks.createEndpoint(req.url(), req.eventTypes());
    }

    @GetMapping
    public List<EndpointView> list() {
        return webhooks.listEndpoints();
    }

    @GetMapping("/{id}/deliveries")
    public List<DeliveryView> deliveries(@PathVariable UUID id) {
        return webhooks.listDeliveries(id);
    }
}