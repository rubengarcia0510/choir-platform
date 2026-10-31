package com.rubengarcia.choir.api;

import com.rubengarcia.choir.formation.application.usecase.FormationVersionLifecycleUseCase;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RequestMapping("/api/choirs/{choirId}/formations/{formationId}/versions")
public class FormationVersionController {

    private final FormationVersionLifecycleUseCase useCase;

    public FormationVersionController(
            FormationVersionLifecycleUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public Flux<FormationVersion> list(
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long formationId,
            Authentication authentication) {
        return useCase.list(authentication.getName(), choirId, formationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FormationVersion> createDraft(
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long formationId,
            @Valid @RequestBody(required = false) CreateDraftRequest request,
            Authentication authentication) {
        return useCase.createDraft(
                authentication.getName(),
                choirId,
                formationId,
                request == null ? null : request.notes());
    }

    @GetMapping("/published")
    public Mono<FormationVersion> published(
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long formationId,
            Authentication authentication) {
        return useCase.published(
                authentication.getName(), choirId, formationId);
    }

    @PostMapping("/{versionId}/publish")
    public Mono<FormationVersion> publish(
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long formationId,
            @PathVariable @Positive long versionId,
            Authentication authentication) {
        return useCase.publish(
                authentication.getName(), choirId, formationId, versionId);
    }

    public record CreateDraftRequest(@Size(max = 2000) String notes) {
    }
}
