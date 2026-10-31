package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.application.usecase.FormationVersionSelectionUseCase;
import com.rubengarcia.choir.formation.domain.FormationVersionSelection;
import com.rubengarcia.choir.formation.domain.FormationVersionSelectionHistory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Validated
@RequestMapping(
        "/api/choirs/{choirId}/concerts/{concertId}"
                + "/repertoire/{repertoireItemId}/formations/selection")
public class FormationVersionSelectionController {

    private final FormationVersionSelectionUseCase selectionUseCase;

    public FormationVersionSelectionController(
            FormationVersionSelectionUseCase selectionUseCase) {
        this.selectionUseCase = selectionUseCase;
    }

    @GetMapping
    public Mono<SelectionResponse> getSelection(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId) {

        return selectionUseCase.findSelection(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId)
                .map(FormationVersionSelectionController::toSelectionResponse);
    }

    @PutMapping
    public Mono<SelectionResponse> selectPublishedVersion(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId,
            @Valid @RequestBody SelectVersionRequest request) {

        return selectionUseCase.selectPublishedVersion(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId,
                        request.formationId(),
                        request.formationVersionId())
                .map(FormationVersionSelectionController::toSelectionResponse);
    }

    @GetMapping("/history")
    public Flux<SelectionHistoryResponse> getSelectionHistory(
            Authentication authentication,
            @PathVariable @Positive long choirId,
            @PathVariable @Positive long concertId,
            @PathVariable @Positive long repertoireItemId) {

        return selectionUseCase.findSelectionHistory(
                        authentication.getName(),
                        choirId,
                        concertId,
                        repertoireItemId)
                .map(FormationVersionSelectionController::toHistoryResponse);
    }

    private static SelectionResponse toSelectionResponse(
            FormationVersionSelection selection) {
        return new SelectionResponse(
                selection.concertId(),
                selection.repertoireItemId(),
                selection.formationId(),
                selection.formationVersionId(),
                selection.selectedAt(),
                selection.hasSelection());
    }

    private static SelectionHistoryResponse toHistoryResponse(
            FormationVersionSelectionHistory history) {
        return new SelectionHistoryResponse(
                history.id(),
                history.concertId(),
                history.repertoireItemId(),
                history.previousFormationId(),
                history.previousFormationVersionId(),
                history.selectedFormationId(),
                history.selectedFormationVersionId(),
                history.actorEmail(),
                history.selectedAt());
    }

    public record SelectVersionRequest(
            @NotNull @Positive Long formationId,
            @NotNull @Positive Long formationVersionId) {
    }

    public record SelectionResponse(
            long concertId,
            long repertoireItemId,
            Long formationId,
            Long formationVersionId,
            java.time.LocalDateTime selectedAt,
            boolean selected) {
    }

    public record SelectionHistoryResponse(
            long id,
            long concertId,
            long repertoireItemId,
            Long previousFormationId,
            Long previousFormationVersionId,
            long selectedFormationId,
            long selectedFormationVersionId,
            String actorEmail,
            java.time.LocalDateTime selectedAt) {
    }
}
