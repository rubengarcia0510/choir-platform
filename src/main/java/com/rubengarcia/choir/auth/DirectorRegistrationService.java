package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class DirectorRegistrationService {

    private final DirectorRepository directorRepository;
    private final DirectorEmailVerificationService verificationService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<VerificationEmailSender> senderProvider;

    public DirectorRegistrationService(
            DirectorRepository directorRepository,
            DirectorEmailVerificationService verificationService,
            PasswordEncoder passwordEncoder,
            ObjectProvider<VerificationEmailSender> senderProvider) {
        this.directorRepository = directorRepository;
        this.verificationService = verificationService;
        this.passwordEncoder = passwordEncoder;
        this.senderProvider = senderProvider;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> register(
            String displayName,
            String email,
            String password,
            String verificationUrlBase) {
        VerificationEmailSender sender = senderProvider.getIfAvailable();

        if (sender == null) {
            return Mono.error(new IllegalStateException(
                    "No hay un proveedor de correo configurado"));
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return directorRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(existing -> false)
                .defaultIfEmpty(true)
                .filter(Boolean::booleanValue)
                .flatMap(ignored -> {
                    Director director = Director.create(
                            displayName.trim(), normalizedEmail);
                    director.setPasswordHash(passwordEncoder.encode(password));
                    director.setEmailVerified(false);

                    return directorRepository.save(director)
                            .flatMap(saved -> verificationService.issueToken(saved)
                                    .flatMap(issued -> {
                                        String verificationUrl = buildVerificationUrl(
                                                verificationUrlBase, issued.token());
                                        return Mono.fromRunnable(() ->
                                                sender.sendVerificationEmail(
                                                        normalizedEmail,
                                                        saved.getDisplayName(),
                                                        verificationUrl,
                                                        issued.expiresAt()));
                                    }));
                })
                .then();
    }

    private String buildVerificationUrl(String baseUrl, String token) {
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
