package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.Choir;
import com.rubengarcia.choir.service.ChoirService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/choirs")
public class ChoirController {

    private final ChoirService choirService;

    public ChoirController(ChoirService choirService) {
        this.choirService = choirService;
    }

    @GetMapping
    public Flux<Choir> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            Authentication authentication) {
        return choirService.list(authentication.getName(), includeArchived);
    }

    @GetMapping("/{choirId}")
    public Mono<Choir> get(
            @PathVariable Long choirId,
            Authentication authentication) {
        return choirService.get(authentication.getName(), choirId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Choir> create(
            @Valid @RequestBody ChoirRequest request,
            Authentication authentication) {
        return choirService.create(
                authentication.getName(), request.name(), request.description());
    }

    @PutMapping("/{choirId}")
    public Mono<Choir> update(
            @PathVariable Long choirId,
            @Valid @RequestBody ChoirRequest request,
            Authentication authentication) {
        return choirService.update(
                authentication.getName(), choirId,
                request.name(), request.description());
    }

    @DeleteMapping("/{choirId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> archive(
            @PathVariable Long choirId,
            Authentication authentication) {
        return choirService.archive(authentication.getName(), choirId);
    }

    public record ChoirRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description) {
    }
}
