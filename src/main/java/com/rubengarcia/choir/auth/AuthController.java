package com.rubengarcia.choir.auth;

import com.rubengarcia.choir.domain.Director;
import com.rubengarcia.choir.repository.DirectorRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ReactiveAuthenticationManager authenticationManager;
    private final ServerSecurityContextRepository securityContextRepository;
    private final DirectorRepository directorRepository;
    private final DirectorRegistrationService registrationService;
    private final DirectorEmailVerificationService verificationService;
    private final ObjectProvider<VerificationEmailSender> verificationEmailSenderProvider;
    private final boolean registrationEnabled;
    private final String verificationUrlBase;

    public AuthController(
            ReactiveAuthenticationManager authenticationManager,
            ServerSecurityContextRepository securityContextRepository,
            DirectorRepository directorRepository,
            DirectorRegistrationService registrationService,
            DirectorEmailVerificationService verificationService,
            ObjectProvider<VerificationEmailSender> verificationEmailSenderProvider,
            @Value("${choir.auth.registration.enabled:false}") boolean registrationEnabled,
            @Value("${choir.auth.verification-url-base:}") String verificationUrlBase) {

        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.directorRepository = directorRepository;
        this.registrationService = registrationService;
        this.verificationService = verificationService;
        this.verificationEmailSenderProvider = verificationEmailSenderProvider;
        this.registrationEnabled = registrationEnabled;
        this.verificationUrlBase = verificationUrlBase;
    }

    @GetMapping("/csrf")
    public Mono<Map<String, String>> csrf(ServerWebExchange exchange) {
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());

        if (csrfToken == null) {
            return Mono.error(new IllegalStateException(
                    "Spring Security no proporcionó el token CSRF"));
        }

        return csrfToken.map(token -> Map.of("token", token.getToken()));
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthApiResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        VerificationEmailSender sender =
                verificationEmailSenderProvider.getIfAvailable();

        if (!registrationEnabled
                || sender == null
                || verificationUrlBase == null
                || verificationUrlBase.isBlank()) {

            return Mono.just(ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body((AuthApiResponse)
                            new ErrorResponse("registration_unavailable")));
        }

        return Mono.fromRunnable(() ->
                        registrationService.register(
                                request.displayName(),
                                request.email(),
                                request.password(),
                                verificationUrlBase))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity
                        .status(HttpStatus.ACCEPTED)
                        .body((AuthApiResponse) new MessageResponse(
                                "If registration is available, a verification email will be sent.")));
    }

    @PostMapping("/verify-email")
    public Mono<ResponseEntity<AuthApiResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {

        return Mono.fromCallable(() ->
                        verificationService.verifyToken(request.token()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(verified -> verified
                        ? verifiedResponse()
                        : invalidVerificationTokenResponse());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthApiResponse>> login(
            @Valid @RequestBody LoginRequest request,
            ServerWebExchange exchange) {

        return authenticationManager.authenticate(
                        new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(
                                request.email(),
                                request.password()))
                .flatMap(authentication ->
                        findDirector(request.email())
                                .flatMap(director ->
                                        exchange.getSession()
                                                .flatMap(session -> {
                                                    session.changeSessionId();

                                                    return securityContextRepository
                                                            .save(
                                                                    exchange,
                                                                    new org.springframework.security
                                                                            .core.context
                                                                            .SecurityContextImpl(
                                                                            authentication))
                                                            .thenReturn(okAuthResponse(director));
                                                })))
                .onErrorResume(
                        org.springframework.security.core
                                .AuthenticationException.class,
                        exception -> Mono.just(unauthorizedResponse()));
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<AuthApiResponse>> me(
            Authentication authentication) {

        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return Mono.just(unauthorizedResponse());
        }

        return findDirector(authentication.getName())
                .map(this::okAuthResponse);
    }

    private Mono<Director> findDirector(String email) {
        return Mono.fromCallable(() ->
                        directorRepository.findByEmailIgnoreCase(email)
                                .orElseThrow(() ->
                                        new IllegalStateException("Director not found")))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<AuthApiResponse> okAuthResponse(Director director) {
        return ResponseEntity.ok(
                (AuthApiResponse) AuthResponse.from(director));
    }

    private ResponseEntity<AuthApiResponse> unauthorizedResponse() {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("invalid_credentials"));
    }

    private ResponseEntity<AuthApiResponse> verifiedResponse() {
        return ResponseEntity.ok(new MessageResponse("email_verified"));
    }

    private ResponseEntity<AuthApiResponse> invalidVerificationTokenResponse() {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("invalid_or_expired_token"));
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 160) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    public record VerifyEmailRequest(
            @NotBlank @Size(max = 128) String token) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password) {
    }

    public record AuthResponse(
            Long id,
            String displayName,
            String email) implements AuthApiResponse {

        static AuthResponse from(Director director) {
            return new AuthResponse(
                    director.getId(),
                    director.getDisplayName(),
                    director.getEmail());
        }
    }

    public record MessageResponse(String message) implements AuthApiResponse {
    }

    public record ErrorResponse(String error) implements AuthApiResponse {
    }
}
