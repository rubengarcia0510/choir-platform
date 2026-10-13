package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.domain.DirectorEmailVerificationToken;
import com.rubengarcia.choir.repository.DirectorEmailVerificationTokenRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

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
    public IssuedToken issueToken(Director director) {
        if (director.getId() == null) {
            throw new IllegalArgumentException(
                    "El director debe estar guardado antes de emitir un token");
        }

        if (director.isEmailVerified()) {
            throw new IllegalStateException(
                    "El email del director ya está verificado");
        }

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plusMinutes(EXPIRATION_MINUTES);

        tokenRepository.deleteAllByDirector_Id(director.getId());
        tokenRepository.save(new DirectorEmailVerificationToken(
                director, hashToken(rawToken), expiresAt));

        return new IssuedToken(rawToken, expiresAt);
    }

    @Transactional
    public boolean verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()
                || rawToken.length() > 128) {
            return false;
        }

        Optional<DirectorEmailVerificationToken> candidate =
                tokenRepository.findByTokenHashForUpdate(hashToken(rawToken));

        if (candidate.isEmpty()) {
            return false;
        }

        DirectorEmailVerificationToken token = candidate.get();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (token.getConsumedAt() != null
                || !token.getExpiresAt().isAfter(now)) {
            return false;
        }

        Director director = token.getDirector();
        if (director.isEmailVerified()) {
            return false;
        }

        token.setConsumedAt(now);
        director.setEmailVerified(true);
        directorRepository.save(director);

        return true;
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
