package com.rubengarcia.choir.auth;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Impide que la aplicación arranque con el registro habilitado si no puede
 * enviar correos de verificación. Con el registro deshabilitado (valor por
 * defecto) no valida nada.
 */
@Component
public class RegistrationStartupValidator implements InitializingBean {

    private final Environment environment;
    private final ObjectProvider<VerificationEmailSender> senderProvider;

    public RegistrationStartupValidator(
            Environment environment,
            ObjectProvider<VerificationEmailSender> senderProvider) {
        this.environment = environment;
        this.senderProvider = senderProvider;
    }

    @Override
    public void afterPropertiesSet() {
        boolean enabled = Boolean.TRUE.equals(environment.getProperty(
                "choir.auth.registration.enabled", Boolean.class, false));

        if (!enabled) {
            return;
        }

        List<String> problems = new ArrayList<>();

        if (senderProvider.getIfAvailable() == null) {
            problems.add("no hay un VerificationEmailSender configurado "
                    + "(habilitá choir.mail.enabled y su configuración)");
        }

        if (!isAbsoluteHttpUrl(environment.getProperty(
                "choir.auth.verification-url-base"))) {
            problems.add("choir.auth.verification-url-base debe ser una URL "
                    + "http(s) absoluta");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "No se puede habilitar el registro: "
                            + String.join("; ", problems));
        }
    }

    private static boolean isAbsoluteHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
