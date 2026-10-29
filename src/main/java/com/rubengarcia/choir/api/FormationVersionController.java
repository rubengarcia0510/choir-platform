package com.rubengarcia.choir.api;

import com.rubengarcia.choir.formation.application.usecase.FormationVersionLifecycleService;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/formations/{formationId}/versions")
public class FormationVersionController {

    private final FormationVersionLifecycleService service;

    public FormationVersionController(FormationVersionLifecycleService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<FormationVersion> list(
            @PathVariable long choirId,
            @PathVariable long formationId,
            Authentication authentication) {
        return service.list(authentication.getName(), choirId, formationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FormationVersion> createDraft(
            @PathVariable long choirId,
            @PathVariable long formationId,
            @Valid @RequestBody(required = false) CreateDraftRequest request,
            Authentication authentication) {
        return service.createDraft(
                authentication.getName(),
                choirId,
                formationId,
                request == null ? null : request.notes());
    }

    @GetMapping("/published")
    public Mono<FormationVersion> published(
            @PathVariable long choirId,
            @PathVariable long formationId,
            Authentication authentication) {
        return service.published(authentication.getName(), choirId, formationId);
    }

    @PostMapping("/{versionId}/publish")
    public Mono<FormationVersion> publish(
            @PathVariable long choirId,
            @PathVariable long formationId,
            @PathVariable long versionId,
            Authentication authentication) {
        return service.publish(
                authentication.getName(), choirId, formationId, versionId);
    }

    public record CreateDraftRequest(@Size(max = 2000) String notes) {
    }
}
