package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.in.FormationDraftManagementUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import com.rubengarcia.choir.formation.domain.Formation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class FormationDraftManagementService
        implements FormationDraftManagementUseCase {

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
}
