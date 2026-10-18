package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.VoicePartMigrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/choirs/{choirId}/voice-part-migration")
public class VoicePartMigrationController {

    private final VoicePartMigrationService migrationService;

    public VoicePartMigrationController(VoicePartMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @GetMapping("/profile")
    public Mono<VoicePartMigrationService.MigrationProfile> profile(
            @PathVariable Long choirId,
            Authentication authentication) {
        return migrationService.profile(authentication.getName(), choirId);
    }

    @PostMapping("/confirm")
    public Mono<VoicePartMigrationService.MigrationResult> confirm(
            @PathVariable Long choirId,
            @Valid @RequestBody ConfirmationRequest request,
            Authentication authentication) {
        return migrationService.confirm(
                authentication.getName(),
                choirId,
                request.confirmed(),
                request.mappings().stream()
                        .map(mapping -> new VoicePartMigrationService.MappingConfirmation(
                                mapping.legacyValue(), mapping.sectionId()))
                        .toList()
        );
    }

    public record ConfirmationRequest(
            boolean confirmed,
            @NotEmpty List<@Valid MappingRequest> mappings) {}

    public record MappingRequest(
            @NotBlank @Size(max = 40) String legacyValue,
            @NotNull @Positive Long sectionId) {}
}
