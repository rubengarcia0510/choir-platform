package com.rubengarcia.choir.api;

import com.rubengarcia.choir.transition.application.usecase.ConcertOptimizationPreferencesService;
import com.rubengarcia.choir.transition.domain.ConcertOptimizationPreferences;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/concerts/{concertId}/optimization-preferences")
public class ConcertOptimizationPreferencesController {

    private final ConcertOptimizationPreferencesService service;

    public ConcertOptimizationPreferencesController(
            ConcertOptimizationPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<PreferencesResponse> get(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.get(authentication.getName(), choirId, concertId)
                .map(PreferencesResponse::from);
    }

    @PutMapping
    public Mono<PreferencesResponse> update(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @Valid @RequestBody PreferencesRequest request,
            Authentication authentication) {

        int total = request.distance()
                + request.crossings()
                + request.compactness()
                + request.sectionContinuity();

        if (total != 100) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Optimization preference weights must sum to 100"));
        }

        ConcertOptimizationPreferences preferences =
                request.toDomain();

        return service.update(
                        authentication.getName(),
                        choirId,
                        concertId,
                        preferences)
                .map(PreferencesResponse::from);
    }

    public record PreferencesRequest(
            @NotNull @Min(0) @Max(100) Integer distance,
            @NotNull @Min(0) @Max(100) Integer crossings,
            @NotNull @Min(0) @Max(100) Integer compactness,
            @NotNull @Min(0) @Max(100) Integer sectionContinuity) {

        public ConcertOptimizationPreferences toDomain() {
            return new ConcertOptimizationPreferences(
                    distance,
                    crossings,
                    compactness,
                    sectionContinuity);
        }
    }

    public record PreferencesResponse(
            int distance,
            int crossings,
            int compactness,
            int sectionContinuity) {

        private static PreferencesResponse from(
                ConcertOptimizationPreferences preferences) {
            return new PreferencesResponse(
                    preferences.distance(),
                    preferences.crossings(),
                    preferences.compactness(),
                    preferences.sectionContinuity());
        }
    }
}
