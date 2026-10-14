package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DirectorRegistrationFailureTests {

    private static final Duration DB_TIMEOUT = Duration.ofSeconds(10);

    private final DirectorRegistrationService registrationService;
    private final DirectorRepository directorRepository;

    @MockitoBean
    private VerificationEmailSender emailSender;

    DirectorRegistrationFailureTests(
            DirectorRegistrationService registrationService,
            DirectorRepository directorRepository) {
        this.registrationService = registrationService;
        this.directorRepository = directorRepository;
    }

    @Test
    void emailDeliveryFailureRollsBackDirectorRegistration() {
        String email = "mail-failure-" + UUID.randomUUID() + "@example.test";

        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailSender)
                .sendVerificationEmail(anyString(), anyString(), anyString(), any());

        assertThrows(
                IllegalStateException.class,
                () -> registrationService.register(
                        "Registration Test",
                        email,
                        "Choir-Test-Password-2026!",
                        "https://example.test/verify").block(DB_TIMEOUT));

        verify(emailSender).sendVerificationEmail(
                anyString(), anyString(), anyString(), any());

        assertFalse(
                directorRepository.findByEmailIgnoreCase(email).block(DB_TIMEOUT) != null,
                "No debe persistir una cuenta si falla el envío del correo");
    }
}
