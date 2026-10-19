package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.Singer;
import com.rubengarcia.choir.service.SingerService;
import com.rubengarcia.choir.service.SingerService.SingerData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
@RequestMapping("/api/choirs/{choirId}/singers")
public class SingerController {

    private final SingerService singerService;

    public SingerController(SingerService singerService) {
        this.singerService = singerService;
    }

    @GetMapping
    public Flux<Singer> list(
            @PathVariable Long choirId,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Authentication authentication) {
        return singerService.list(
                authentication.getName(), choirId, includeInactive);
    }

    @GetMapping("/{singerId}")
    public Mono<Singer> get(
            @PathVariable Long choirId,
            @PathVariable Long singerId,
            Authentication authentication) {
        return singerService.get(
                authentication.getName(), choirId, singerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Singer> create(
            @PathVariable Long choirId,
            @Valid @RequestBody SingerRequest request,
            Authentication authentication) {
        return singerService.create(
                authentication.getName(), choirId, request.toData());
    }

    @PutMapping("/{singerId}")
    public Mono<Singer> update(
            @PathVariable Long choirId,
            @PathVariable Long singerId,
            @Valid @RequestBody SingerRequest request,
            Authentication authentication) {
        return singerService.update(
                authentication.getName(), choirId, singerId, request.toData());
    }

    @DeleteMapping("/{singerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deactivate(
            @PathVariable Long choirId,
            @PathVariable Long singerId,
            Authentication authentication) {
        return singerService.deactivate(
                authentication.getName(), choirId, singerId);
    }

    @PostMapping("/{singerId}/reactivate")
    public Mono<Singer> reactivate(
            @PathVariable Long choirId,
            @PathVariable Long singerId,
            Authentication authentication) {
        return singerService.reactivate(
                authentication.getName(), choirId, singerId);
    }

    public record SingerRequest(
            @NotBlank @Size(max = 160) String displayName,
            @Email @Size(max = 254) String email,
            @Size(max = 40) String voicePart,
            Long voiceSectionId,
            @Size(max = 120) String mobility,
            @Size(max = 1000) String restrictions) {

        SingerData toData() {
            return new SingerData(
                    displayName, email, voicePart,
                    voiceSectionId, mobility, restrictions);
        }
    }
}
