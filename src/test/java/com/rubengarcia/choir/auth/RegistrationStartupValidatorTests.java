package com.rubengarcia.choir.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RegistrationStartupValidatorTests {

    private static final String VALID_URL = "https://app.example.test/verify";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RegistrationStartupValidator.class);

    @Test
    void registrationIsDisabledByDefaultAndNeedsNoMailConfiguration() {
        runner.run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void startupFailsWhenRegistrationIsEnabledWithoutEmailSender() {
        runner.withPropertyValues(
                "choir.auth.registration.enabled=true",
                "choir.auth.verification-url-base=" + VALID_URL)
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "VerificationEmailSender"));
    }

    @Test
    void startupFailsWhenVerificationUrlBaseIsBlank() {
        runner.withBean(VerificationEmailSender.class,
                        () -> mock(VerificationEmailSender.class))
                .withPropertyValues("choir.auth.registration.enabled=true")
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "verification-url-base"));
    }

    @Test
    void startupFailsWhenVerificationUrlBaseIsNotAnAbsoluteHttpUrl() {
        runner.withBean(VerificationEmailSender.class,
                        () -> mock(VerificationEmailSender.class))
                .withPropertyValues(
                        "choir.auth.registration.enabled=true",
                        "choir.auth.verification-url-base=/verify")
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "verification-url-base"));
    }

    @Test
    void startsWhenRegistrationHasSenderAndVerificationUrl() {
        runner.withBean(VerificationEmailSender.class,
                        () -> mock(VerificationEmailSender.class))
                .withPropertyValues(
                        "choir.auth.registration.enabled=true",
                        "choir.auth.verification-url-base=" + VALID_URL)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    private static void assertFailureMentions(Throwable failure, String text) {
        assertNotNull(failure, "El contexto debía fallar al arrancar");

        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        assertTrue(
                current.getMessage().contains(text),
                "El mensaje debía mencionar " + text + " pero fue: "
                        + current.getMessage());
    }
}
