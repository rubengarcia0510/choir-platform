package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Concert;
import com.rubengarcia.choir.repository.ConcertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class ConcertManagementServiceImpl implements ConcertManagementService {

    private final ConcertRepository concertRepository;
    private final ResourceAuthorizationService authorizationService;

    public ConcertManagementServiceImpl(
            ConcertRepository concertRepository,
            ResourceAuthorizationService authorizationService) {
        this.concertRepository = concertRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Flux<Concert> list(String email, long choirId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .thenMany(concertRepository.findByChoirId(choirId));
    }

    @Override
    public Mono<Concert> get(String email, long choirId, long concertId) {
        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .then(concertRepository.findById(concertId))
                .switchIfEmpty(Mono.error(notFound()));
    }

    @Override
    public Mono<Concert> create(
            String email, long choirId, String title,
            LocalDate concertDate, String venue) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(Mono.defer(() -> {
                    Concert concert = new Concert();
                    concert.setChoirId(choirId);
                    concert.setTitle(title.trim());
                    concert.setConcertDate(concertDate);
                    concert.setVenue(normalizeVenue(venue));
                    concert.setStatus("PLANNED");
                    return concertRepository.save(concert);
                }));
    }

    @Override
    public Mono<Concert> update(
            String email, long choirId, long concertId,
            String title, LocalDate concertDate, String venue,
            String status) {
        return get(email, choirId, concertId)
                .flatMap(concert -> {
                    if (!"PLANNED".equals(concert.getStatus())
                            && !"REHEARSAL".equals(concert.getStatus())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Solo se pueden editar conciertos en configuración o ensayo"));
                    }

                    if (!"PLANNED".equals(status) && !"REHEARSAL".equals(status)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "El estado debe ser PLANNED o REHEARSAL"));
                    }

                    concert.setTitle(title.trim());
                    concert.setConcertDate(concertDate);
                    concert.setVenue(normalizeVenue(venue));
                    concert.setStatus(status);
                    return concertRepository.save(concert);
                });
    }

    @Override
    public Mono<Void> archive(String email, long choirId, long concertId) {
        return get(email, choirId, concertId)
                .flatMap(concert -> {
                    if ("ARCHIVED".equals(concert.getStatus())) {
                        return Mono.empty();
                    }
                    concert.setStatus("ARCHIVED");
                    return concertRepository.save(concert).then();
                });
    }

    private String normalizeVenue(String venue) {
        return venue == null || venue.isBlank() ? null : venue.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Concierto inexistente o no autorizado");
    }
}
