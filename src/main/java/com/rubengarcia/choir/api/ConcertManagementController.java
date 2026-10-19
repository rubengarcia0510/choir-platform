package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.Concert;
import com.rubengarcia.choir.service.ConcertManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/choirs/{choirId}/concerts")
public class ConcertManagementController {

    private final ConcertManagementService service;

    public ConcertManagementController(ConcertManagementService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<Concert> list(
            @PathVariable long choirId,
            Authentication authentication) {
        return service.list(authentication.getName(), choirId);
    }

    @GetMapping("/{concertId}")
    public Mono<Concert> get(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.get(authentication.getName(), choirId, concertId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Concert> create(
            @PathVariable long choirId,
            @Valid @RequestBody ConcertRequest request,
            Authentication authentication) {
        return service.create(
                authentication.getName(), choirId,
                request.title(), request.concertDate(), request.venue());
    }

    @PutMapping("/{concertId}")
    public Mono<Concert> update(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @Valid @RequestBody ConcertRequest request,
            Authentication authentication) {
        return service.update(
                authentication.getName(), choirId, concertId,
                request.title(), request.concertDate(),
                request.venue(), request.status());
    }

    @DeleteMapping("/{concertId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> archive(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.archive(authentication.getName(), choirId, concertId);
    }

    public record ConcertRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull LocalDate concertDate,
            @Size(max = 250) String venue,
            String status) {
    }
}
