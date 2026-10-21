package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.application.port.in.FormationDraftManagementUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.domain.Formation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping(
        "/api/choirs/{choirId}/concerts/{concertId}"
                + "/repertoire/{repertoireItemId}/formations")
public class FormationDraftController {

    private final FormationDraftManagementUseCase formationDraftManagement;

    public FormationDraftController(
            FormationDraftManagementUseCase formationDraftManagement) {
        this.formationDraftManagement = formationDraftManagement;
    }

    @GetMapping
    public Flux<FormationSummaryResponse> list(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId) {

        return formationDraftManagement.list(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId)
                .map(FormationDraftController::toSummary);
    }

    @PostMapping
    public Mono<ResponseEntity<CreateFormationResponse>> create(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @Valid @RequestBody CreateFormationRequest request) {

        return formationDraftManagement.create(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        request.name().trim(),
                        request.description())
                .map(created -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(toCreateResponse(created)));
    }

    private static FormationSummaryResponse toSummary(Formation formation) {
        return new FormationSummaryResponse(
                formation.getId(),
                formation.getName(),
                formation.getDescription());
    }

    private static CreateFormationResponse toCreateResponse(
            FormationCatalogPort.CreatedFormation created) {

        Formation formation = created.formation();

        return new CreateFormationResponse(
                formation.getId(),
                formation.getName(),
                formation.getDescription(),
                created.draftVersion().getId(),
                created.draftVersion().getVersionNumber(),
                created.draftVersion().getStatus());
    }

    public record CreateFormationRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description) {
    }

    public record FormationSummaryResponse(
            Long id,
            String name,
            String description) {
    }

    public record CreateFormationResponse(
            Long id,
            String name,
            String description,
            Long draftVersionId,
            int versionNumber,
            String status) {
    }
}
