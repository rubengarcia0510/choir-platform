package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.DirectorEmailVerificationToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface DirectorEmailVerificationTokenRepository
        extends ReactiveCrudRepository<DirectorEmailVerificationToken, Long> {

    Mono<Void> deleteAllByDirectorId(Long directorId);

    Mono<DirectorEmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE director_email_verification_tokens
            SET consumed_at = :now
            WHERE id = :id
              AND consumed_at IS NULL
              AND expires_at > :now
            """)
    Mono<Integer> consumeIfValid(
            @Param("id") Long id,
            @Param("now") LocalDateTime now);
}
