package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.in.FormationDraftManagementUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.domain.Formation;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationDraftManagementService
        implements FormationDraftManagementUseCase {

    private static final BigDecimal MIN_COORDINATE = BigDecimal.ZERO;
    private static final BigDecimal MAX_COORDINATE = BigDecimal.valueOf(100);
    private static final int MAX_LABEL_LENGTH = 80;

    private final FormationCatalogPort formationCatalog;
    private final FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    public FormationDraftManagementService(
            FormationCatalogPort formationCatalog,
            FormationOccurrenceAuthorizationPort occurrenceAuthorization) {
        this.formationCatalog = formationCatalog;
        this.occurrenceAuthorization = occurrenceAuthorization;
    }

    @Override
    public Flux<Formation> list(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId) {

        return Flux.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .thenMany(Flux.defer(() ->
                                formationCatalog.findForOccurrence(
                                        choirId, concertId, repertoireItemId))));
    }

    @Override
    public Mono<FormationCatalogPort.CreatedFormation> create(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description) {

        return Mono.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .then(Mono.defer(() ->
                                formationCatalog.createWithInitialDraft(
                                        choirId,
                                        concertId,
                                        repertoireItemId,
                                        name,
                                        description))));
    }

    @Override
    public Mono<FormationCatalogPort.DraftDetails> getDraft(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId) {

        return Mono.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                email, choirId, concertId, repertoireItemId)
                        .then(Mono.defer(() ->
                                formationCatalog.findDraftForOccurrence(
                                        choirId,
                                        concertId,
                                        repertoireItemId,
                                        formationId))));
    }

    @Override
    public Mono<FormationCatalogPort.DraftDetails> replaceDraftPositions(
            String email,
            long choirId,
            long concertId,
            long repertoireItemId,
            long formationId,
            long versionId,
            List<FormationCatalogPort.PositionUpdate> positions) {

        return Mono.defer(() -> {
            validatePositions(positions);

            return occurrenceAuthorization.requireOwnedOccurrence(
                            email, choirId, concertId, repertoireItemId)
                    .then(Mono.defer(() ->
                            formationCatalog.replaceDraftPositions(
                                    choirId,
                                    concertId,
                                    repertoireItemId,
                                    formationId,
                                    versionId,
                                    List.copyOf(positions))));
        });
    }

    private void validatePositions(
            List<FormationCatalogPort.PositionUpdate> positions) {

        if (positions == null) {
            throw new IllegalArgumentException(
                    "La lista de posiciones es obligatoria");
        }

        Set<Long> singerIds = new HashSet<>();

        for (FormationCatalogPort.PositionUpdate position : positions) {
            if (position == null) {
                throw new IllegalArgumentException(
                        "La lista contiene una posición nula");
            }

            Long singerId = position.singerId();
            if (singerId == null || singerId <= 0) {
                throw new IllegalArgumentException(
                        "Cada posición requiere un singerId positivo");
            }

            if (!singerIds.add(singerId)) {
                throw new IllegalArgumentException(
                        "No se puede repetir un cantante en la misma formación");
            }

            validateCoordinate(position.x(), "x");
            validateCoordinate(position.y(), "y");

            if (position.positionLabel() != null
                    && position.positionLabel().length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException(
                        "La etiqueta no puede superar 80 caracteres");
            }
        }
    }

    private void validateCoordinate(BigDecimal value, String name) {
        if (value == null
                || value.compareTo(MIN_COORDINATE) < 0
                || value.compareTo(MAX_COORDINATE) > 0) {
            throw new IllegalArgumentException(
                    "La coordenada " + name + " debe estar entre 0 y 100");
        }
    }
}
