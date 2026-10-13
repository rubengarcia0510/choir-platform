package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void register(
            String displayName,
            String email,
            String password,
            String verificationUrlBase) {

        VerificationEmailSender sender = senderProvider.getIfAvailable();

        if (sender == null) {
            throw new IllegalStateException(
                    "No hay un proveedor de correo configurado");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        if (directorRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return;
        }

        Director director = Director.create(
                displayName.trim(),
                normalizedEmail);

        director.setPasswordHash(passwordEncoder.encode(password));
        director.setEmailVerified(false);

        directorRepository.saveAndFlush(director);

        DirectorEmailVerificationService.IssuedToken issuedToken =
                verificationService.issueToken(director);

        String verificationUrl = buildVerificationUrl(
                verificationUrlBase,
                issuedToken.token());

        sender.sendVerificationEmail(
                normalizedEmail,
                director.getDisplayName(),
                verificationUrl,
                issuedToken.expiresAt());
    }

    private String buildVerificationUrl(String baseUrl, String token) {
        String separator = baseUrl.contains("?") ? "&" : "?";

        return baseUrl
                + separator
                + "token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
