package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.VocalSection;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import com.rubengarcia.choir.repository.VocalSectionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class VocalSectionService {

    private final DirectorRepository directorRepository;
    private final ChoirRepository choirRepository;
    private final VocalSectionRepository vocalSectionRepository;

    public VocalSectionService(
            DirectorRepository directorRepository,
            ChoirRepository choirRepository,
            VocalSectionRepository vocalSectionRepository) {
        this.directorRepository = directorRepository;
        this.choirRepository = choirRepository;
        this.vocalSectionRepository = vocalSectionRepository;
    }

    public Flux<VocalSection> list(String authenticatedEmail, Long choirId, boolean activeOnly) {
        return ownedChoir(authenticatedEmail, choirId)
                .flatMapMany(choir -> activeOnly
                        ? vocalSectionRepository.findByChoirIdAndActiveTrueOrderBySortOrderAscNameAsc(choir.getId())
                        : vocalSectionRepository.findByChoirIdOrderBySortOrderAscNameAsc(choir.getId()));
    }

    public Mono<VocalSection> create(
            String authenticatedEmail, Long choirId, String name, int sortOrder) {
        return ownedChoir(authenticatedEmail, choirId)
                .flatMap(choir -> {
                    VocalSection section = new VocalSection();
                    section.setChoirId(choir.getId());
                    section.setName(name.trim());
                    section.setSortOrder(sortOrder);
                    section.setActive(true);
                    return vocalSectionRepository.save(section);
                });
    }

    public Mono<VocalSection> update(
            String authenticatedEmail,
            Long choirId,
            Long sectionId,
            String name,
            int sortOrder,
            boolean active) {
        return ownedChoir(authenticatedEmail, choirId)
                .then(Mono.defer(() -> vocalSectionRepository.findById(sectionId)
                        .filter(section -> choirId.equals(section.getChoirId()))
                        .switchIfEmpty(Mono.error(notFound()))
                        .flatMap(section -> {
                            section.setName(name.trim());
                            section.setSortOrder(sortOrder);
                            section.setActive(active);
                            return vocalSectionRepository.save(section);
                        })));
    }

    public Mono<Void> deactivate(
            String authenticatedEmail, Long choirId, Long sectionId) {
        return ownedChoir(authenticatedEmail, choirId)
                .then(Mono.defer(() -> vocalSectionRepository.findById(sectionId)
                        .filter(section -> choirId.equals(section.getChoirId()))
                        .switchIfEmpty(Mono.error(notFound()))
                        .flatMap(section -> {
                            section.setActive(false);
                            return vocalSectionRepository.save(section);
                        })))
                .then();
    }

    private Mono<Choir> ownedChoir(String email, Long choirId) {
        if (email == null || email.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return directorRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(notFound()))
                .flatMap(director -> choirRepository.findById(choirId)
                        .filter(choir -> director.getId().equals(choir.getDirectorId()))
                        .switchIfEmpty(Mono.error(notFound())));
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
