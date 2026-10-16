package com.rubengarcia.choir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigurationValidationTests {

    private static final List<String> REQUIRED_DATABASE_VARIABLES = List.of(
            "DATABASE_URL",
            "R2DBC_URL",
            "DATABASE_USERNAME",
            "DATABASE_PASSWORD"
    );

    @Test
    void startupFailsAndIdentifiesEachMissingProductionDatabaseVariable() {
        for (String missingVariable : REQUIRED_DATABASE_VARIABLES) {
            Throwable startupFailure = startWithout(missingVariable);

            assertNotNull(
                    startupFailure,
                    () -> "La aplicación arrancó aunque faltaba " + missingVariable
            );

            String diagnostic = causeMessages(startupFailure);

            assertTrue(
                    diagnostic.contains(missingVariable),
                    () -> "El arranque falló, pero no identificó la variable "
                            + missingVariable + ". Causas: " + diagnostic
            );
        }
    }

    private Throwable startWithout(String missingVariable) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.flyway.enabled", "false");
        properties.put("spring.main.web-application-type", "none");

        properties.put(
                "DATABASE_URL",
                "jdbc:postgresql://127.0.0.1:5432/choir"
        );
        properties.put(
                "R2DBC_URL",
                "r2dbc:postgresql://127.0.0.1:5432/choir"
        );
        properties.put("DATABASE_USERNAME", "test_user");
        properties.put("DATABASE_PASSWORD", "test_password");

        properties.remove(missingVariable);

        StandardEnvironment isolatedEnvironment = new StandardEnvironment();
        isolatedEnvironment.getPropertySources().remove(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME
        );
        isolatedEnvironment.getPropertySources().remove(
                StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME
        );

        SpringApplication application =
                new SpringApplication(ChoirPlatformApplication.class);
        application.setEnvironment(isolatedEnvironment);
        application.setAdditionalProfiles("prod");
        application.setDefaultProperties(properties);
        application.setRegisterShutdownHook(false);

        ConfigurableApplicationContext context = null;

        try {
            context = application.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;

        while (current != null) {
            messages.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage())
                    .append('\n');

            if (current.getCause() == current) {
                break;
            }

            current = current.getCause();
        }

        return messages.toString();
    }
}
