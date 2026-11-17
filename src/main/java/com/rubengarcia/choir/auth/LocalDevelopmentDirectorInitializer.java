package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
@Profile("local")
@ConditionalOnProperty(
        prefix = "choir.auth.local-director",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class LocalDevelopmentDirectorInitializer implements ApplicationRunner {

    private final DirectorRepository directorRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public LocalDevelopmentDirectorInitializer(
            DirectorRepository directorRepository,
            PasswordEncoder passwordEncoder,
            Environment environment
    ) {
        this.directorRepository = directorRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String name = requiredEnvironmentValue("CHOIR_DEV_DIRECTOR_NAME").trim();
        String email = requiredEnvironmentValue("CHOIR_DEV_DIRECTOR_EMAIL")
                .trim()
                .toLowerCase(Locale.ROOT);
        String password = requiredEnvironmentValue("CHOIR_DEV_DIRECTOR_PASSWORD");

        directorRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.defer(() ->
                        directorRepository.save(createDirector(name, email, password))
                ))
                .block();
    }

    private Director createDirector(String name, String email, String password) {
        Director director = Director.create(name, email);
        director.setPasswordHash(passwordEncoder.encode(password));
        director.setEmailVerified(true);
        return director;
    }

    private String requiredEnvironmentValue(String variableName) {
        String value = environment.getProperty(variableName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + variableName
            );
        }
        return value;
    }
}
