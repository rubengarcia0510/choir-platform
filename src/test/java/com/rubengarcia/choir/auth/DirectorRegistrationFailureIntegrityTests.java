package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DirectorRegistrationFailureIntegrityTests {

    private static final Duration DB_TIMEOUT = Duration.ofSeconds(10);

    private final DirectorRegistrationService registrationService;
    private final DirectorRepository directorRepository;
    private final DatabaseClient databaseClient;

    @MockitoBean
    private VerificationEmailSender emailSender;

    DirectorRegistrationFailureIntegrityTests(
            DirectorRegistrationService registrationService,
            DirectorRepository directorRepository,
            DatabaseClient databaseClient) {
        this.registrationService = registrationService;
        this.directorRepository = directorRepository;
        this.databaseClient = databaseClient;
    }

    @Test
    void emailDeliveryFailureLeavesNoDirectorOrVerificationToken() {
        String email = "mail-failure-integrity-" + UUID.randomUUID() + "@example.test";

        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailSender)
                .sendVerificationEmail(anyString(), anyString(), anyString(), any());

        assertThrows(
                IllegalStateException.class,
                () -> registrationService.register(
                        "Registration Integrity Test",
                        email,
                        "Choir-Test-Password-2026!",
                        "https://example.test/verify").block(DB_TIMEOUT));

        verify(emailSender).sendVerificationEmail(
                anyString(), anyString(), anyString(), any());

        assertNull(
                directorRepository.findByEmailIgnoreCase(email).block(DB_TIMEOUT),
                "No debe persistir el director si falla el envío del correo");

        Long tokenCount = databaseClient.sql("""
                        SELECT COUNT(*)
                        FROM director_email_verification_tokens t
                        JOIN directors d ON d.id = t.director_id
                        WHERE d.email = :email
                        """)
                .bind("email", email)
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .block(DB_TIMEOUT);

        assertEquals(
                0L,
                tokenCount,
                "No deben quedar tokens de verificación si falla el envío del correo");
    }
}
