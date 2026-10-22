package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.application.port.in.FormationDuplicationUseCase;
import com.rubengarcia.choir.formation.domain.Formation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping(
        "/api/choirs/{choirId}/concerts/{concertId}"
                + "/repertoire/{repertoireItemId}/formations")
public class FormationDuplicationController {

    private final FormationDuplicationUseCase duplicationUseCase;

    public FormationDuplicationController(
            FormationDuplicationUseCase duplicationUseCase) {
        this.duplicationUseCase = duplicationUseCase;
    }

    @PostMapping("/{formationId}/duplicate")
    public Mono<ResponseEntity<FormationDraftController.CreateFormationResponse>> duplicate(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @PathVariable @Positive long formationId,
            @Valid @RequestBody DuplicateFormationRequest request) {
        return duplicationUseCase.duplicate(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        formationId,
                        request.sourceVersionId(),
                        request.name().trim(),
                        request.description())
                .map(created -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(toResponse(created)))
                .onErrorMap(IllegalArgumentException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, error.getMessage(), error))
                .onErrorMap(IllegalStateException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.CONFLICT, error.getMessage(), error));
    }

    private static FormationDraftController.CreateFormationResponse toResponse(
            FormationDuplicationUseCase.CreatedFormation created) {
        Formation formation = created.formation();
        return new FormationDraftController.CreateFormationResponse(
                formation.getId(),
                formation.getName(),
                formation.getDescription(),
                created.draftVersion().getId(),
                created.draftVersion().getVersionNumber(),
                created.draftVersion().getStatus());
    }

    public record DuplicateFormationRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            @Positive Long sourceVersionId) {
    }
}
