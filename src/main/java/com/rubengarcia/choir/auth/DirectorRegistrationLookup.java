package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DirectorRegistrationLookup {

    private final DirectorRepository directorRepository;

    public DirectorRegistrationLookup(DirectorRepository directorRepository) {
        this.directorRepository = directorRepository;
    }

    public Mono<Director> findByEmailIgnoreCase(String email) {
        return directorRepository.findByEmailIgnoreCase(email);
    }
}
