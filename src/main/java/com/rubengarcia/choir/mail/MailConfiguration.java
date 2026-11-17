package com.rubengarcia.choir.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Registra el adaptador SMTP solo cuando {@code choir.mail.enabled=true} y la
 * configuración necesaria está completa. Si falta algo, la aplicación no
 * arranca, para que el registro no quede habilitado sin poder enviar correos.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChoirMailProperties.class)
public class MailConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "choir.mail", name = "enabled", havingValue = "true")
    SmtpVerificationEmailSender smtpVerificationEmailSender(
            ChoirMailProperties properties,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Environment environment) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        validate(properties, mailSender != null, environment);

        return new SmtpVerificationEmailSender(
                mailSender, properties.getFrom(), properties.getFromName());
    }

    static void validate(
            ChoirMailProperties properties,
            boolean mailSenderAvailable,
            Environment environment) {
        List<String> problems = new ArrayList<>();

        String from = properties.getFrom();
        if (from == null || !from.matches("^[^@\\s]+@[^@\\s]+$")) {
            problems.add("choir.mail.from debe ser una dirección de correo");
        }
        if (properties.getFromName() == null
                || properties.getFromName().isBlank()) {
            problems.add("choir.mail.from-name no puede estar vacío");
        }
        if (!mailSenderAvailable) {
            problems.add("spring.mail.host es obligatorio");
        }

        Integer port = environment.getProperty("spring.mail.port", Integer.class);
        if (port == null || port < 1 || port > 65535) {
            problems.add("spring.mail.port es obligatorio (1-65535)");
        }

        String username = environment.getProperty("spring.mail.username");
        String password = environment.getProperty("spring.mail.password");
        if (username != null && !username.isBlank()
                && (password == null || password.isBlank())) {
            problems.add("spring.mail.password es obligatorio si se define "
                    + "spring.mail.username");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Configuración de correo incompleta con choir.mail.enabled=true: "
                            + String.join("; ", problems));
        }
    }
}
