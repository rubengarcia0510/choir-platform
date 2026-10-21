package com.rubengarcia.choir.formation.adapter.out.persistence;

import com.rubengarcia.choir.formation.application.port.out.FormationCatalogPort;
import com.rubengarcia.choir.formation.domain.Formation;
import com.rubengarcia.choir.formation.domain.FormationVersion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class FormationCatalogPersistenceAdapter implements FormationCatalogPort {

    private final FormationRepository formationRepository;
    private final FormationVersionRepository formationVersionRepository;

    public FormationCatalogPersistenceAdapter(
            FormationRepository formationRepository,
            FormationVersionRepository formationVersionRepository) {
        this.formationRepository = formationRepository;
        this.formationVersionRepository = formationVersionRepository;
    }

    @Override
    public Flux<Formation> findForOccurrence(
            long choirId,
            long concertId,
            long repertoireItemId) {

        return formationRepository
                .findByChoirIdAndConcertIdAndRepertoireItemId(
                        choirId,
                        concertId,
                        repertoireItemId);
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<CreatedFormation> createWithInitialDraft(
            long choirId,
            long concertId,
            long repertoireItemId,
            String name,
            String description) {

        Formation formation = new Formation();
        formation.setChoirId(choirId);
        formation.setConcertId(concertId);
        formation.setRepertoireItemId(repertoireItemId);
        formation.setName(name);
        formation.setDescription(description);

        return formationRepository.save(formation)
                .flatMap(savedFormation -> {
                    FormationVersion draft = new FormationVersion();
                    draft.setFormationId(savedFormation.getId());
                    draft.setVersionNumber(1);
                    draft.setStatus("DRAFT");

                    return formationVersionRepository.save(draft)
                            .map(savedDraft ->
                                    new CreatedFormation(savedFormation, savedDraft));
                });
    }
}
