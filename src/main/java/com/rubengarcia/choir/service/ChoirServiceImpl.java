package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.ChoirRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ChoirServiceImpl implements ChoirService {

    private final ChoirRepository choirRepository;
    private final DirectorRepository directorRepository;

    public ChoirServiceImpl(
            ChoirRepository choirRepository,
            DirectorRepository directorRepository) {
        this.choirRepository = choirRepository;
        this.directorRepository = directorRepository;
    }

    public Flux<Choir> list(String email, boolean includeArchived) {
        return director(email).flatMapMany(director -> includeArchived
                ? choirRepository.findByDirectorId(director.getId())
                : choirRepository.findByDirectorIdAndArchivedFalse(director.getId()));
    }

    public Mono<Choir> get(String email, Long choirId) {
        return ownedChoir(email, choirId);
    }

    public Mono<Choir> create(String email, String name, String description) {
        return director(email).flatMap(director -> {
            Choir choir = new Choir(
                    director.getId(),
                    name.trim(),
                    normalizeDescription(description)
            );
            return choirRepository.save(choir);
        });
    }

    public Mono<Choir> update(
            String email, Long choirId, String name, String description) {
        return ownedChoir(email, choirId)
                .flatMap(choir -> {
                    if (choir.isArchived()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT, "No se puede editar un coro archivado"));
                    }
                    choir.setName(name.trim());
                    choir.setDescription(normalizeDescription(description));
                    return choirRepository.save(choir);
                });
    }

    public Mono<Void> archive(String email, Long choirId) {
        return ownedChoir(email, choirId)
                .flatMap(choir -> {
                    if (choir.isArchived()) {
                        return Mono.empty();
                    }
                    choir.setArchived(true);
                    return choirRepository.save(choir).then();
                });
    }

    private Mono<Director> director(String email) {
        if (email == null || email.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }
        return directorRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(notFound()));
    }

    private Mono<Choir> ownedChoir(String email, Long choirId) {
        return director(email).flatMap(director ->
                choirRepository.findByIdAndDirectorId(choirId, director.getId())
                        .switchIfEmpty(Mono.error(notFound())));
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank()
                ? null : description.trim();
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Coro inexistente o no autorizado");
    }
}
