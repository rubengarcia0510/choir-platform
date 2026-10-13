package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.domain.DirectorEmailVerificationToken;
import com.rubengarcia.choir.repository.DirectorEmailVerificationTokenRepository;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DirectorEmailVerificationServiceTests {

    private final DirectorEmailVerificationService verificationService;
    private final DirectorRepository directorRepository;
    private final DirectorEmailVerificationTokenRepository tokenRepository;

    DirectorEmailVerificationServiceTests(
            DirectorEmailVerificationService verificationService,
            DirectorRepository directorRepository,
            DirectorEmailVerificationTokenRepository tokenRepository) {
        this.verificationService = verificationService;
        this.directorRepository = directorRepository;
        this.tokenRepository = tokenRepository;
    }

    @Test
    void validTokenCanBeUsedOnlyOnce() {
        Director director = createUnverifiedDirector();

        DirectorEmailVerificationService.IssuedToken issued =
                verificationService.issueToken(director);

        assertTrue(verificationService.verifyToken(issued.token()));
        assertFalse(verificationService.verifyToken(issued.token()));

        Director persisted = directorRepository.findById(director.getId())
                .orElseThrow();

        assertTrue(persisted.isEmailVerified());
    }

    @Test
    void expiredTokenIsRejected() {
        Director director = createUnverifiedDirector();
        String rawToken = "expired-" + UUID.randomUUID();

        DirectorEmailVerificationToken expired =
                new DirectorEmailVerificationToken(
                        director,
                        hashToken(rawToken),
                        LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));

        tokenRepository.saveAndFlush(expired);

        assertFalse(verificationService.verifyToken(rawToken));

        Director persisted = directorRepository.findById(director.getId())
                .orElseThrow();

        assertFalse(persisted.isEmailVerified());
    }

    @Test
    void issuingNewTokenInvalidatesPreviousToken() {
        Director director = createUnverifiedDirector();

        DirectorEmailVerificationService.IssuedToken first =
                verificationService.issueToken(director);
        DirectorEmailVerificationService.IssuedToken second =
                verificationService.issueToken(director);

        assertNotEquals(first.token(), second.token());
        assertFalse(verificationService.verifyToken(first.token()));
        assertTrue(verificationService.verifyToken(second.token()));
    }

    @Test
    void malformedTokensAreRejected() {
        assertFalse(verificationService.verifyToken(null));
        assertFalse(verificationService.verifyToken(""));
        assertFalse(verificationService.verifyToken("   "));
        assertFalse(verificationService.verifyToken("x".repeat(129)));
        assertFalse(verificationService.verifyToken(
                "unknown-" + UUID.randomUUID()));
    }

    @Test
    void concurrentVerificationCanSucceedOnlyOnce() throws Exception {
        Director director = createUnverifiedDirector();

        DirectorEmailVerificationService.IssuedToken issued =
                verificationService.issueToken(director);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return verificationService.verifyToken(issued.token());
            });

            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return verificationService.verifyToken(issued.token());
            });

            start.countDown();

            boolean firstResult = first.get(10, TimeUnit.SECONDS);
            boolean secondResult = second.get(10, TimeUnit.SECONDS);

            assertEquals(1, (firstResult ? 1 : 0) + (secondResult ? 1 : 0));

            Director persisted = directorRepository.findById(director.getId())
                    .orElseThrow();

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

        Director saved = directorRepository.saveAndFlush(director);
        assertNotNull(saved.getId());

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
