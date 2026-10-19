package com.rubengarcia.choir.service;

import com.rubengarcia.choir.repository.ConcertSingerInvitationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ConcertSingerInvitationServiceImpl
        implements ConcertSingerInvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcertSingerInvitationRepository repository;
    private final ResourceAuthorizationService authorizationService;

    public ConcertSingerInvitationServiceImpl(
            ConcertSingerInvitationRepository repository,
            ResourceAuthorizationService authorizationService) {
        this.repository = repository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Mono<CreatedInvitation> create(
            String email, long choirId, long concertId,
            long singerId, LocalDateTime expiresAt) {

        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha de vencimiento debe ser futura"));
        }

        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .then(repository.findConcertStatus(choirId, concertId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND)))
                        .flatMap(status -> {
                            if (!"PUBLISHED".equals(status)) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Solo se pueden invitar coreutas a conciertos publicados"));
                            }

                            return repository.findActiveSinger(choirId, singerId)
                                    .flatMap(ignored -> createAndPersist(
                                            choirId, concertId, singerId, expiresAt))
                                    .switchIfEmpty(Mono.error(
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Coreuta inexistente o inactivo")));
                        }));
    }

    private Mono<CreatedInvitation> createAndPersist(
            long choirId, long concertId, long singerId,
            LocalDateTime expiresAt) {

        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomBytes);
        String hash = sha256(token);

        return repository.insertInvitation(
                        choirId, concertId, singerId, hash, expiresAt)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo guardar la invitación")))
                .map(id -> new CreatedInvitation(
                        id, concertId, singerId, token, expiresAt));
    }

    @Override
    public Mono<Void> revoke(
            String email, long choirId, long concertId, long invitationId) {

        return authorizationService.requireOwnedConcert(email, choirId, concertId)
                .then(repository.revoke(choirId, concertId, invitationId))
                .flatMap(count -> count > 0
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Invitación inexistente o ya revocada")));
    }

    @Override
    public Mono<ConcertView> viewByToken(String token) {
        if (token == null || token.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));
        }

        return repository.findUsableInvitation(sha256(token))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Invitación inexistente, vencida o revocada")))
                .flatMap(invitation -> Mono.zip(
                        repository.findConcertDetails(
                                invitation.choirId(), invitation.concertId())
                                .switchIfEmpty(Mono.error(
                                        new ResponseStatusException(HttpStatus.NOT_FOUND))),
                        repository.findInvitedSingerSnapshotName(
                                invitation.concertId(), invitation.singerId()),
                        repository.findRepertoire(
                                invitation.choirId(), invitation.concertId()),
                        repository.findCast(
                                invitation.concertId(), invitation.singerId()),
                        repository.findVocalAssignments(
                                invitation.concertId(), invitation.singerId()))
                        .map(result -> new ConcertView(
                                invitation.concertId(),
                                result.getT1().title(),
                                result.getT1().date(),
                                result.getT1().venue(),
                                result.getT2(),
                                result.getT3(),
                                result.getT4(),
                                result.getT5())));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible", exception);
        }
    }
}
