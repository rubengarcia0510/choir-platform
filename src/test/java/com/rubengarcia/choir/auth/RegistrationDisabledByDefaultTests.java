package com.rubengarcia.choir.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestConstructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RegistrationDisabledByDefaultTests {

    private final Environment environment;
    private final ObjectProvider<VerificationEmailSender> senderProvider;

    RegistrationDisabledByDefaultTests(
            Environment environment,
            ObjectProvider<VerificationEmailSender> senderProvider) {
        this.environment = environment;
        this.senderProvider = senderProvider;
    }

    @Test
    void registrationAndMailAreDisabledByDefault() {
        assertEquals(false, environment.getProperty(
                "choir.auth.registration.enabled", Boolean.class));
        assertEquals(false, environment.getProperty(
                "choir.mail.enabled", Boolean.class));
    }

    @Test
    void noEmailSenderIsRegisteredWithTheDefaultConfiguration() {
        assertNull(senderProvider.getIfAvailable());
    }
}
