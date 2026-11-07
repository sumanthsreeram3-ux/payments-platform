package com.sumanth.payments.payments_platform.assistant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assistant")
public class AssistantController {

    public record AskRequest(@NotBlank @Size(max = 500) String question) {}

    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) { this.assistant = assistant; }

    @PostMapping("/ask")
    public AssistantService.Answer ask(@Valid @RequestBody AskRequest request) {
        return assistant.ask(request.question().trim());
    }
}