package com.rubengarcia.choir.mail;

import com.rubengarcia.choir.auth.VerificationEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MailConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MailConfiguration.class)
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));

    @Test
    void adapterIsAbsentWhenMailIsNotEnabled() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(VerificationEmailSender.class).isEmpty());
        });
    }

    @Test
    void adapterIsAbsentWhenMailIsExplicitlyDisabled() {
        runner.withPropertyValues("choir.mail.enabled=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(VerificationEmailSender.class).isEmpty());
        });
    }

    @Test
    void adapterIsCreatedWhenConfigurationIsComplete() {
        runner.withPropertyValues(
                "choir.mail.enabled=true",
                "choir.mail.from=no-reply@example.test",
                "spring.mail.port=587").run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(VerificationEmailSender.class));
            assertTrue(context.getBean(VerificationEmailSender.class)
                    instanceof SmtpVerificationEmailSender);
        });
    }

    @Test
    void startupFailsWhenSenderAddressIsMissing() {
        runner.withPropertyValues("choir.mail.enabled=true", "spring.mail.port=587")
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "choir.mail.from"));
    }

    @Test
    void startupFailsWhenPortIsMissing() {
        runner.withPropertyValues(
                "choir.mail.enabled=true",
                "choir.mail.from=no-reply@example.test")
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "spring.mail.port"));
    }

    @Test
    void startupFailsWhenSmtpHostIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(MailConfiguration.class)
                .withPropertyValues(
                        "choir.mail.enabled=true",
                        "choir.mail.from=no-reply@example.test",
                        "spring.mail.port=587")
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "spring.mail.host"));
    }

    @Test
    void startupFailsWhenUsernameHasNoPassword() {
        runner.withPropertyValues(
                "choir.mail.enabled=true",
                "choir.mail.from=no-reply@example.test",
                "spring.mail.port=587",
                "spring.mail.username=smtp-user")
                .run(context -> assertFailureMentions(
                        context.getStartupFailure(), "spring.mail.password"));
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
