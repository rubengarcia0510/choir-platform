package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.ConcertSingerInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
public class ConcertSingerInvitationController {

    private final ConcertSingerInvitationService service;

    public ConcertSingerInvitationController(ConcertSingerInvitationService service) {
        this.service = service;
    }

    public record CreateRequest(@NotNull Long singerId, LocalDateTime expiresAt) {}

    @PostMapping("/api/choirs/{choirId}/concerts/{concertId}/invitations")
    public Mono<ConcertSingerInvitationService.CreatedInvitation> create(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @Valid @RequestBody CreateRequest request,
            Authentication authentication) {

        if (authentication == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return service.create(authentication.getName(), choirId, concertId,
                request.singerId(), request.expiresAt());
    }

    @DeleteMapping("/api/choirs/{choirId}/concerts/{concertId}/invitations/{invitationId}")
    public Mono<Void> revoke(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long invitationId,
            Authentication authentication) {

        if (authentication == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return service.revoke(
                authentication.getName(), choirId, concertId, invitationId);
    }

    @GetMapping("/api/public/concert-invitations/{token}")
    public Mono<ConcertSingerInvitationService.ConcertView> view(
            @PathVariable String token) {
        return service.viewByToken(token);
    }
}
