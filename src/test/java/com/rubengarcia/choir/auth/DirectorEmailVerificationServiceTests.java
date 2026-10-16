package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.domain.DirectorEmailVerificationToken;
import com.rubengarcia.choir.repository.DirectorEmailVerificationTokenRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DirectorEmailVerificationServiceTests {

    private static final Duration DB_TIMEOUT = Duration.ofSeconds(10);

    private final DirectorEmailVerificationService verificationService;
    private final DirectorRepository directorRepository;
    private final DirectorEmailVerificationTokenRepository tokenRepository;
    private final JdbcTemplate jdbcTemplate;
    private final List<Long> createdDirectorIds = new ArrayList<>();

    DirectorEmailVerificationServiceTests(
            DirectorEmailVerificationService verificationService,
            DirectorRepository directorRepository,
            DirectorEmailVerificationTokenRepository tokenRepository,
            JdbcTemplate jdbcTemplate) {
        this.verificationService = verificationService;
        this.directorRepository = directorRepository;
        this.tokenRepository = tokenRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void cleanup() {
        for (Long directorId : createdDirectorIds) {
            jdbcTemplate.update(
                    "DELETE FROM director_email_verification_tokens WHERE director_id = ?",
                    directorId);
            jdbcTemplate.update(
                    "DELETE FROM directors WHERE id = ?",
                    directorId);
        }
        createdDirectorIds.clear();
    }

    @Test
    void validTokenCanBeUsedOnlyOnce() {
        Director director = createUnverifiedDirector();

        DirectorEmailVerificationService.IssuedToken issued =
                verificationService.issueToken(director).block(DB_TIMEOUT);
        assertNotNull(issued);

        assertTrue(verificationService.verifyToken(issued.token()).block(DB_TIMEOUT));
        assertFalse(verificationService.verifyToken(issued.token()).block(DB_TIMEOUT));

        Director persisted =
                directorRepository.findById(director.getId()).block(DB_TIMEOUT);
        assertNotNull(persisted);
        assertTrue(persisted.isEmailVerified());
    }

    @Test
    void expiredTokenIsRejected() {
        Director director = createUnverifiedDirector();
        String rawToken = "expired-" + UUID.randomUUID();

        DirectorEmailVerificationToken expired =
                new DirectorEmailVerificationToken(
                        director.getId(),
                        hashToken(rawToken),
                        LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));

        tokenRepository.save(expired).block(DB_TIMEOUT);

        assertFalse(verificationService.verifyToken(rawToken).block(DB_TIMEOUT));

        Director persisted =
                directorRepository.findById(director.getId()).block(DB_TIMEOUT);
        assertNotNull(persisted);
        assertFalse(persisted.isEmailVerified());
    }

    @Test
    void issuingNewTokenInvalidatesPreviousToken() {
        Director director = createUnverifiedDirector();

        DirectorEmailVerificationService.IssuedToken first =
                verificationService.issueToken(director).block(DB_TIMEOUT);
        DirectorEmailVerificationService.IssuedToken second =
                verificationService.issueToken(director).block(DB_TIMEOUT);

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.token(), second.token());
        assertFalse(verificationService.verifyToken(first.token()).block(DB_TIMEOUT));
        assertTrue(verificationService.verifyToken(second.token()).block(DB_TIMEOUT));
    }

    @Test
    void malformedTokensAreRejected() {
        assertFalse(verificationService.verifyToken(null).block(DB_TIMEOUT));
        assertFalse(verificationService.verifyToken("").block(DB_TIMEOUT));
        assertFalse(verificationService.verifyToken("   ").block(DB_TIMEOUT));
        assertFalse(verificationService.verifyToken("x".repeat(129)).block(DB_TIMEOUT));
        assertFalse(verificationService.verifyToken(
                "unknown-" + UUID.randomUUID()).block(DB_TIMEOUT));
    }

    @Test
    void concurrentVerificationCanSucceedOnlyOnce() throws Exception {
        Director director = createUnverifiedDirector();
        DirectorEmailVerificationService.IssuedToken issued =
                verificationService.issueToken(director).block(DB_TIMEOUT);
        assertNotNull(issued);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return verificationService.verifyToken(issued.token()).block(DB_TIMEOUT);
            });

            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return verificationService.verifyToken(issued.token()).block(DB_TIMEOUT);
            });

            start.countDown();

            boolean firstResult = first.get(15, TimeUnit.SECONDS);
            boolean secondResult = second.get(15, TimeUnit.SECONDS);

            assertEquals(1, (firstResult ? 1 : 0) + (secondResult ? 1 : 0));

            Director persisted =
                    directorRepository.findById(director.getId()).block(DB_TIMEOUT);
            assertNotNull(persisted);
            assertTrue(persisted.isEmailVerified());
        } finally {
            executor.shutdownNow();
        }
    }

    private Director createUnverifiedDirector() {
        Director director = Director.create(
                "Verification Test",
                "verification-" + UUID.randomUUID() + "@example.test");
        director.setEmailVerified(false);

        Director saved = directorRepository.save(director).block(DB_TIMEOUT);
        assertNotNull(saved);
        assertNotNull(saved.getId());

        createdDirectorIds.add(saved.getId());
        return saved;
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
