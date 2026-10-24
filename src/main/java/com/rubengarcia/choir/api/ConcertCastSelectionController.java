package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.ConcertCastMember;
import com.rubengarcia.choir.service.ConcertCastSelectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/choirs/{choirId}/concerts/{concertId}/cast")
public class ConcertCastSelectionController {

    private final ConcertCastSelectionService service;

    public ConcertCastSelectionController(ConcertCastSelectionService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<ConcertCastMember> getCast(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.getCast(
                authentication.getName(), choirId, concertId);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> replaceSelection(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @Valid @RequestBody CastSelectionRequest request,
            Authentication authentication) {

        return service.replaceSelection(
                authentication.getName(),
                choirId,
                concertId,
                request.singerIds());
    }

    public record CastSelectionRequest(
            @NotNull List<@NotNull Long> singerIds) {
    }
}
