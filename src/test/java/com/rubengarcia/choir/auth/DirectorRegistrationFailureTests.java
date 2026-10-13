package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.repository.DirectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    private final DirectorRegistrationService registrationService;
    private final DirectorRepository directorRepository;
    private final PasswordEncoder passwordEncoder;

    @MockitoBean
    private VerificationEmailSender emailSender;

    DirectorRegistrationFailureTests(
            DirectorRegistrationService registrationService,
            DirectorRepository directorRepository,
            PasswordEncoder passwordEncoder) {
        this.registrationService = registrationService;
        this.directorRepository = directorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void emailDeliveryFailureRollsBackDirectorRegistration() {
        String email = "mail-failure-" + UUID.randomUUID() + "@example.test";

        doThrow(new IllegalStateException("SMTP unavailable"))
                .when(emailSender)
                .sendVerificationEmail(
                        anyString(),
                        anyString(),
                        anyString(),
                        any());

        assertThrows(
                IllegalStateException.class,
                () -> registrationService.register(
                        "Registration Test",
                        email,
                        "Choir-Test-Password-2026!",
                        "https://example.test/verify"));

        verify(emailSender).sendVerificationEmail(
                anyString(),
                anyString(),
                anyString(),
                any());

        assertFalse(
                directorRepository.findByEmailIgnoreCase(email).isPresent(),
                "No debe persistir una cuenta si falla el envío del correo");
    }
}
