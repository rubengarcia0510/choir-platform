package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.application.port.in.FormationMobilityConstraintUseCase;
import com.rubengarcia.choir.formation.domain.FormationMobilityConstraint;
import com.rubengarcia.choir.formation.domain.MobilityRegion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
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

/**
 * Restricciones de movilidad (región rectangular X/Y por coreuta) de una
 * formación. La visibilidad no se expone todavía: su modelo de relación
 * protegido/obstructor está pendiente.
 */
@RestController
@Validated
@RequestMapping(
        "/api/choirs/{choirId}/concerts/{concertId}"
                + "/repertoire/{repertoireItemId}/formations"
                + "/{formationId}/constraints/physical")
public class FormationMobilityConstraintController {

    private static final String NOT_FOUND_MESSAGE =
            "No existe la restricción indicada";

    private final FormationMobilityConstraintUseCase useCase;

    public FormationMobilityConstraintController(
            FormationMobilityConstraintUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public Flux<MobilityConstraintResponse> list(
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
                .map(FormationMobilityConstraintController::toResponse);
    }

    @PostMapping("/mobility")
    public Mono<ResponseEntity<MobilityConstraintResponse>> create(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId,
            @Valid @RequestBody CreateMobilityConstraintRequest request) {
        return Mono.defer(() -> useCase.create(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        request.singerId(),
                        region(request.minX(), request.maxX(),
                                request.minY(), request.maxY())))
                .map(FormationMobilityConstraintController::toResponse)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response))
                .onErrorMap(IllegalArgumentException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                error.getMessage(),
                                error));
    }

    @PutMapping("/{constraintId}")
    public Mono<MobilityConstraintResponse> update(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId,
            @PathVariable @Positive long constraintId,
            @Valid @RequestBody UpdateMobilityConstraintRequest request) {
        return Mono.defer(() -> useCase.update(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        constraintId,
                        region(request.minX(), request.maxX(),
                                request.minY(), request.maxY()),
                        request.active()))
                .map(FormationMobilityConstraintController::toResponse)
                .onErrorMap(IllegalArgumentException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                error.getMessage(),
                                error))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE)));
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
                                HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE)));
    }

    private static MobilityRegion region(
            BigDecimal minX,
            BigDecimal maxX,
            BigDecimal minY,
            BigDecimal maxY) {
        return new MobilityRegion(minX, maxX, minY, maxY);
    }

    private static MobilityConstraintResponse toResponse(
            FormationMobilityConstraint constraint) {
        return new MobilityConstraintResponse(
                constraint.id(),
                constraint.formationId(),
                constraint.singerId(),
                constraint.region().minX(),
                constraint.region().maxX(),
                constraint.region().minY(),
                constraint.region().maxY(),
                constraint.active(),
                constraint.createdAt());
    }

    public record CreateMobilityConstraintRequest(
            @NotNull @Positive Long singerId,
            @NotNull BigDecimal minX,
            @NotNull BigDecimal maxX,
            @NotNull BigDecimal minY,
            @NotNull BigDecimal maxY) {
    }

    public record UpdateMobilityConstraintRequest(
            @NotNull BigDecimal minX,
            @NotNull BigDecimal maxX,
            @NotNull BigDecimal minY,
            @NotNull BigDecimal maxY,
            @NotNull Boolean active) {
    }

    public record MobilityConstraintResponse(
            Long id,
            Long formationId,
            Long singerId,
            BigDecimal minX,
            BigDecimal maxX,
            BigDecimal minY,
            BigDecimal maxY,
            boolean active,
            LocalDateTime createdAt) {
    }
}
