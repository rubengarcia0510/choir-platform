package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.domain.DirectorEmailVerificationToken;
import com.rubengarcia.choir.repository.DirectorEmailVerificationTokenRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class DirectorEmailVerificationService {

    private static final int TOKEN_BYTES = 32;
    private static final int EXPIRATION_MINUTES = 30;

    private final DirectorEmailVerificationTokenRepository tokenRepository;
    private final DirectorRepository directorRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public DirectorEmailVerificationService(
            DirectorEmailVerificationTokenRepository tokenRepository,
            DirectorRepository directorRepository) {
        this.tokenRepository = tokenRepository;
        this.directorRepository = directorRepository;
    }

    @Transactional
    public Mono<IssuedToken> issueToken(Director director) {
        if (director.getId() == null) {
            return Mono.error(new IllegalArgumentException(
                    "El director debe estar guardado antes de emitir un token"));
        }

        if (director.isEmailVerified()) {
            return Mono.error(new IllegalStateException(
                    "El email del director ya está verificado"));
        }

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        LocalDateTime expiresAt =
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(EXPIRATION_MINUTES);

        DirectorEmailVerificationToken token =
                new DirectorEmailVerificationToken(
                        director.getId(),
                        hashToken(rawToken),
                        expiresAt);

        return tokenRepository.deleteAllByDirectorId(director.getId())
                .then(tokenRepository.save(token))
                .thenReturn(new IssuedToken(rawToken, expiresAt));
    }

    @Transactional
    public Mono<Boolean> verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            return Mono.just(false);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        return tokenRepository.findByTokenHash(hashToken(rawToken))
                .flatMap(token -> {
                    if (token.getConsumedAt() != null
                            || !token.getExpiresAt().isAfter(now)) {
                        return Mono.just(false);
                    }

                    return tokenRepository.consumeIfValid(token.getId(), now)
                            .defaultIfEmpty(0)
                            .flatMap(updated -> {
                                if (updated != 1) {
                                    return Mono.just(false);
                                }

                                return directorRepository.findById(token.getDirectorId())
                                        .flatMap(director -> {
                                            if (director.isEmailVerified()) {
                                                return Mono.just(false);
                                            }

                                            director.setEmailVerified(true);
                                            return directorRepository.save(director)
                                                    .thenReturn(true);
                                        })
                                        .defaultIfEmpty(false);
                            });
                })
                .defaultIfEmpty(false);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 no está disponible", exception);
        }
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {
    }
}
