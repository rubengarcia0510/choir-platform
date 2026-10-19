package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.ConcertRepertoireEntry;
import com.rubengarcia.choir.service.ConcertRepertoireService;
import com.rubengarcia.choir.service.ConcertRepertoireService.SongSelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/choirs/{choirId}/concerts/{concertId}/repertoire")
public class ConcertRepertoireController {

    private final ConcertRepertoireService service;

    public ConcertRepertoireController(ConcertRepertoireService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<ConcertRepertoireEntry> getRepertoire(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.getRepertoire(
                authentication.getName(), choirId, concertId);
    }

    @PutMapping
    public Mono<Void> replaceRepertoire(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @Valid @RequestBody RepertoireRequest request,
            Authentication authentication) {
        return service.replaceRepertoire(
                authentication.getName(), choirId, concertId,
                request.songs().stream()
                        .map(song -> new SongSelection(
                                song.repertoireItemId(),
                                song.durationMinutes(),
                                song.stageRequirements()))
                        .toList());
    }

    public record RepertoireRequest(
            @NotNull List<@NotNull @Valid SongRequest> songs) {
    }

    public record SongRequest(
            @NotNull Long repertoireItemId,
            @Positive Integer durationMinutes,
            @Size(max = 1000) String stageRequirements) {
    }
}
