package com.rubengarcia.choir.formation.application.usecase;

import com.rubengarcia.choir.formation.application.port.in.FormationDuplicationUseCase;
import com.rubengarcia.choir.formation.application.port.out.FormationDuplicationPort;
import com.rubengarcia.choir.formation.application.port.out.FormationOccurrenceAuthorizationPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class FormationDuplicationService implements FormationDuplicationUseCase {

    private final FormationDuplicationPort duplicationPort;
    private final FormationOccurrenceAuthorizationPort occurrenceAuthorization;

    public FormationDuplicationService(
            FormationDuplicationPort duplicationPort,
            FormationOccurrenceAuthorizationPort occurrenceAuthorization) {
        this.duplicationPort = duplicationPort;
        this.occurrenceAuthorization = occurrenceAuthorization;
    }

    @Override
    public Mono<FormationDuplicationUseCase.CreatedFormation> duplicate(
            String directorEmail,
            long choirId,
            long concertId,
            long repertoireItemId,
            long sourceFormationId,
            Long sourceVersionId,
            String name,
            String description) {
        return Mono.defer(() ->
                occurrenceAuthorization.requireOwnedOccurrence(
                                directorEmail, choirId, concertId, repertoireItemId)
                        .then(Mono.defer(() -> duplicationPort.duplicate(
                                choirId,
                                concertId,
                                repertoireItemId,
                                sourceFormationId,
                                sourceVersionId,
                                name,
                                description)
                        .map(created ->
                                new FormationDuplicationUseCase.CreatedFormation(
                                        created.formation(),
                                        created.draftVersion())))));
    }
}
