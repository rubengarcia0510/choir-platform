package com.rubengarcia.choir.service;

import com.rubengarcia.choir.repository.ConcertCastSelectionRepository;
import com.rubengarcia.choir.repository.ConcertPublicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class ConcertPublicationServiceImpl implements ConcertPublicationService {

    private final ResourceAuthorizationService authorizationService;
    private final ConcertPublicationRepository publicationRepository;
    private final ConcertCastSelectionRepository castSelectionRepository;

    public ConcertPublicationServiceImpl(
            ResourceAuthorizationService authorizationService,
            ConcertPublicationRepository publicationRepository,
            ConcertCastSelectionRepository castSelectionRepository) {
        this.authorizationService = authorizationService;
        this.publicationRepository = publicationRepository;
        this.castSelectionRepository = castSelectionRepository;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<ConcertPublicationService.Publication> publish(String email, long choirId, long concertId) {
        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .then(publicationRepository.findStatus(choirId, concertId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Concierto inexistente o fuera del coro del director")))
                        .flatMap(status -> {
                            if (!"PLANNED".equals(status)
                                    && !"REHEARSAL".equals(status)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Solo se pueden publicar conciertos en estado "
                                                + "PLANNED o REHEARSAL"));
                            }
                            return capture(choirId, concertId);
                        }));
    }

    private Mono<ConcertPublicationService.Publication> capture(
            long choirId, long concertId) {
        return publicationRepository.markPublished(choirId, concertId)
                .flatMap(updated -> {
                    if (!updated) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "El concierto ya fue publicado o cambió de estado"));
                    }
                    return castSelectionRepository.captureCast(concertId)
                            .then(publicationRepository.captureVocalAssignments(
                                    choirId, concertId))
                            .then(publicationRepository.findPublication(
                                    choirId, concertId))
                            .map(publication -> new ConcertPublicationService.Publication(
                                    concertId,
                                    publication.status(),
                                    publication.publishedAt(),
                                    publication.castSnapshots(),
                                    publication.vocalAssignmentSnapshots()));
                });
    }
}
