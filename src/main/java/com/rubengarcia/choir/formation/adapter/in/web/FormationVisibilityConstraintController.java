package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.application.port.in.FormationVisibilityConstraintUseCase;
import com.rubengarcia.choir.formation.domain.FormationVisibilityConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping(
        "/api/choirs/{choirId}/concerts/{concertId}"
                + "/repertoire/{repertoireItemId}/formations"
                + "/{formationId}/visibility-constraints")
public class FormationVisibilityConstraintController {

    private final FormationVisibilityConstraintUseCase useCase;

    public FormationVisibilityConstraintController(
            FormationVisibilityConstraintUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public Flux<ConstraintResponse> list(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId) {
        return useCase.list(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId)
                .map(FormationVisibilityConstraintController::toResponse);
    }

    @PostMapping
    public Mono<ResponseEntity<ConstraintResponse>> create(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId,
            @Valid @RequestBody CreateConstraintRequest request) {
        return Mono.defer(() -> useCase.create(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        request.protectedSingerId()))
                .map(FormationVisibilityConstraintController::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(response))
                .onErrorMap(IllegalArgumentException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, error.getMessage(), error));
    }

    @PutMapping("/{constraintId}")
    public Mono<ConstraintResponse> update(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId,
            @PathVariable @Positive long constraintId,
            @Valid @RequestBody UpdateConstraintRequest request) {
        return Mono.defer(() -> useCase.update(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        constraintId,
                        request.active()))
                .map(FormationVisibilityConstraintController::toResponse)
                .onErrorMap(IllegalArgumentException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, error.getMessage(), error))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe la restricción indicada")));
    }

    @DeleteMapping("/{constraintId}")
    public Mono<ResponseEntity<Void>> delete(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId,
            @PathVariable @Positive long constraintId) {
        return useCase.delete(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        constraintId)
                .flatMap(deleted -> deleted
                        ? Mono.just(ResponseEntity.noContent().<Void>build())
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No existe la restricción indicada")));
    }

    private static ConstraintResponse toResponse(
            FormationVisibilityConstraint constraint) {
        return new ConstraintResponse(
                constraint.id(),
                constraint.formationId(),
                constraint.protectedSingerId(),
                constraint.active(),
                constraint.createdAt());
    }

    public record CreateConstraintRequest(@NotNull @Positive Long protectedSingerId) {
    }

    public record UpdateConstraintRequest(@NotNull Boolean active) {
    }

    public record ConstraintResponse(
            Long id,
            Long formationId,
            Long protectedSingerId,
            boolean active,
            LocalDateTime createdAt) {
    }
}
