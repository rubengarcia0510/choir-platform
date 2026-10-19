package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.ConcertRepertoire;
import com.rubengarcia.choir.domain.ConcertRepertoireEntry;
import com.rubengarcia.choir.repository.ConcertRepertoireRepository;
import com.rubengarcia.choir.repository.RepertoireItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConcertRepertoireServiceImpl implements ConcertRepertoireService {

    private final ResourceAuthorizationService authorizationService;
    private final ConcertRepertoireRepository concertRepertoireRepository;
    private final RepertoireItemRepository repertoireItemRepository;

    public ConcertRepertoireServiceImpl(
            ResourceAuthorizationService authorizationService,
            ConcertRepertoireRepository concertRepertoireRepository,
            RepertoireItemRepository repertoireItemRepository) {
        this.authorizationService = authorizationService;
        this.concertRepertoireRepository = concertRepertoireRepository;
        this.repertoireItemRepository = repertoireItemRepository;
    }

    @Override
    public Flux<ConcertRepertoireEntry> getRepertoire(
            String email, long choirId, long concertId) {
        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .thenMany(concertRepertoireRepository.findConcertEntries(concertId));
    }

    @Override
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> replaceRepertoire(
            String email,
            long choirId,
            long concertId,
            List<SongSelection> songs) {
        if (songs == null || songs.stream().anyMatch(song -> song == null)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La lista de canciones no puede ser nula ni contener elementos nulos"));
        }
        Set<Long> selectedIds = new HashSet<>();
        if (songs.stream().anyMatch(song -> !selectedIds.add(song.repertoireItemId()))) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El repertorio no puede contener canciones duplicadas"));
        }

        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .then(concertRepertoireRepository
                        .findStatusForUpdate(choirId, concertId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Concierto inexistente o no autorizado")))
                        .flatMap(status -> {
                            if (!"PLANNED".equals(status)
                                    && !"REHEARSAL".equals(status)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Solo se puede modificar el repertorio en configuración o ensayo"));
                            }
                            return replaceAfterValidation(
                                    choirId, concertId, songs, selectedIds);
                        }));
    }

    private Mono<Void> replaceAfterValidation(
            long choirId,
            long concertId,
            List<SongSelection> songs,
            Set<Long> selectedIds) {
        return repertoireItemRepository.findByChoirId(choirId)
                .filter(item -> selectedIds.contains(item.getId()))
                .map(item -> item.getId())
                .collectList()
                .flatMap(validIds -> {
                    if (validIds.size() != selectedIds.size()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Todas las canciones deben pertenecer al coro del concierto"));
                    }
                    return concertRepertoireRepository.deleteByConcertId(concertId)
                            .thenMany(Flux.range(0, songs.size())
                                    .concatMap(index -> {
                                        SongSelection song = songs.get(index);
                                        return concertRepertoireRepository.insert(
                                                new ConcertRepertoire(
                                                        concertId,
                                                        song.repertoireItemId(),
                                                        index,
                                                        song.durationMinutes(),
                                                        normalize(song.stageRequirements())));
                                    }))
                            .then();
                });
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
