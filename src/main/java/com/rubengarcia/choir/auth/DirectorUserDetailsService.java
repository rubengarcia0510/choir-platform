package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class DirectorUserDetailsService implements ReactiveUserDetailsService {

    private final DirectorRepository directorRepository;

    public DirectorUserDetailsService(DirectorRepository directorRepository) {
        this.directorRepository = directorRepository;
    }

    @Override
    public Mono<UserDetails> findByUsername(String email) {
        return Mono.fromCallable(() -> directorRepository.findByEmailIgnoreCase(email.trim())
                        .filter(director -> director.getPasswordHash() != null
                                && !director.getPasswordHash().isBlank()
                        && director.isEmailVerified())
                        .map(this::toUserDetails)
                        .orElseThrow(() ->
                                new UsernameNotFoundException("Invalid credentials")))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private UserDetails toUserDetails(Director director) {
        return User.withUsername(director.getEmail())
                .password(director.getPasswordHash())
                .roles("DIRECTOR")
                .build();
    }
}
