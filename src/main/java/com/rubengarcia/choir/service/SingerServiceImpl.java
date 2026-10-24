package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.Singer;
import com.rubengarcia.choir.domain.VocalSection;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.SingerRepository;
import com.rubengarcia.choir.repository.VocalSectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Service
public class SingerServiceImpl implements SingerService {

    private final SingerRepository singerRepository;
    private final ChoirRepository choirRepository;
    private final VocalSectionRepository vocalSectionRepository;
    private final ResourceAuthorizationService authorizationService;

    public SingerServiceImpl(
            SingerRepository singerRepository,
            ChoirRepository choirRepository,
            VocalSectionRepository vocalSectionRepository,
            ResourceAuthorizationService authorizationService) {
        this.singerRepository = singerRepository;
        this.choirRepository = choirRepository;
        this.vocalSectionRepository = vocalSectionRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Flux<Singer> list(
            String email, Long choirId, boolean includeInactive) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(choirRepository.findById(choirId)
                        .switchIfEmpty(Mono.error(notFound())))
                .flatMapMany(choir -> includeInactive
                        ? singerRepository.findByChoirId(choirId)
                        : singerRepository.findByChoirIdAndActiveTrue(choirId));
    }

    @Override
    public Mono<Singer> get(
            String email, Long choirId, Long singerId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(findSinger(choirId, singerId));
    }

    @Override
    public Mono<Singer> create(
            String email, Long choirId, SingerData data) {
        return requireEditableChoir(email, choirId)
                .then(validateSection(choirId, data.voiceSectionId()))
                .then(Mono.defer(() -> singerRepository.save(new Singer(
                        choirId,
                        data.displayName().trim(),
                        normalize(data.email()),
                        normalize(data.voicePart()),
                        data.voiceSectionId(),
                        normalize(data.mobility()),
                        normalize(data.restrictions())))));
    }

    @Override
    public Mono<Singer> update(
            String email, Long choirId, Long singerId, SingerData data) {
        return requireEditableChoir(email, choirId)
                .then(findSinger(choirId, singerId))
                .flatMap(singer -> {
                    if (!singer.isActive()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No se puede editar un coreuta inactivo"));
                    }
                    return validateSection(choirId, data.voiceSectionId())
                            .then(Mono.defer(() -> {
                                singer.setDisplayName(data.displayName().trim());
                                singer.setEmail(normalize(data.email()));
                                singer.setVoicePart(normalize(data.voicePart()));
                                singer.setVoiceSectionId(data.voiceSectionId());
                                singer.setMobility(normalize(data.mobility()));
                                singer.setRestrictions(normalize(data.restrictions()));
                                return singerRepository.save(singer);
                            }));
                });
    }

    @Override
    public Mono<Void> deactivate(
            String email, Long choirId, Long singerId) {
        return requireEditableChoir(email, choirId)
                .then(findSinger(choirId, singerId))
                .flatMap(singer -> {
                    if (!singer.isActive()) {
                        return Mono.empty();
                    }
                    singer.setActive(false);
                    return singerRepository.save(singer).then();
                });
    }

    @Override
    public Mono<Singer> reactivate(
            String email, Long choirId, Long singerId) {
        return requireEditableChoir(email, choirId)
                .then(findSinger(choirId, singerId))
                .flatMap(singer -> {
                    if (singer.isActive()) {
                        return Mono.just(singer);
                    }
                    return validateSection(choirId, singer.getVoiceSectionId())
                            .then(Mono.defer(() -> {
                                singer.setActive(true);
                                return singerRepository.save(singer);
                            }));
                });
    }

    private Mono<Choir> requireEditableChoir(String email, Long choirId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(choirRepository.findById(choirId)
                        .switchIfEmpty(Mono.error(notFound())))
                .flatMap(choir -> choir.isArchived()
                        ? Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "No se puede modificar el elenco de un coro archivado"))
                        : Mono.just(choir));
    }

    private Mono<Singer> findSinger(Long choirId, Long singerId) {
        return singerRepository.findByIdAndChoirId(singerId, choirId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Coreuta inexistente en este coro")));
    }

    private Mono<Void> validateSection(Long choirId, Long sectionId) {
        if (sectionId == null) {
            return Mono.empty();
        }
        return vocalSectionRepository.findById(sectionId)
                .filter(section -> Objects.equals(section.getChoirId(), choirId)
                        && section.isActive())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La sección vocal no existe, está inactiva o pertenece a otro coro")))
                .then();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Coro inexistente o no autorizado");
    }
}
