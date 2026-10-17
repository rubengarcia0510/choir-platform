package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.VocalSection;
import com.rubengarcia.choir.service.VocalSectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/vocal-sections")
public class VocalSectionController {

    private final VocalSectionService vocalSectionService;

    public VocalSectionController(VocalSectionService vocalSectionService) {
        this.vocalSectionService = vocalSectionService;
    }

    @GetMapping
    public Flux<VocalSection> list(
            @PathVariable Long choirId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            Authentication authentication) {
        return vocalSectionService.list(
                authentication.getName(), choirId, activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<VocalSection> create(
            @PathVariable Long choirId,
            @Valid @RequestBody VocalSectionRequest request,
            Authentication authentication) {
        return vocalSectionService.create(
                authentication.getName(),
                choirId,
                request.name(),
                request.sortOrder());
    }

    @PutMapping("/{sectionId}")
    public Mono<VocalSection> update(
            @PathVariable Long choirId,
            @PathVariable Long sectionId,
            @Valid @RequestBody UpdateVocalSectionRequest request,
            Authentication authentication) {
        return vocalSectionService.update(
                authentication.getName(),
                choirId,
                sectionId,
                request.name(),
                request.sortOrder(),
                request.active());
    }

    @DeleteMapping("/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deactivate(
            @PathVariable Long choirId,
            @PathVariable Long sectionId,
            Authentication authentication) {
        return vocalSectionService.deactivate(
                authentication.getName(), choirId, sectionId);
    }

    public record VocalSectionRequest(
            @NotBlank @Size(max = 80) String name,
            @PositiveOrZero int sortOrder) {
    }

    public record UpdateVocalSectionRequest(
            @NotBlank @Size(max = 80) String name,
            @PositiveOrZero int sortOrder,
            boolean active) {
    }
}
