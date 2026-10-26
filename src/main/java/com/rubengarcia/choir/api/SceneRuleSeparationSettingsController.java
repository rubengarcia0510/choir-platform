package com.rubengarcia.choir.api;

import com.rubengarcia.choir.scene.application.usecase.SceneRuleSeparationSettingsService;
import com.rubengarcia.choir.scene.application.usecase.SceneRuleSeparationSettingsService.SettingView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/scene-rule-settings/separation-threshold")
public class SceneRuleSeparationSettingsController {

    private final SceneRuleSeparationSettingsService service;

    public SceneRuleSeparationSettingsController(
            SceneRuleSeparationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<SettingView> get(
            @PathVariable long choirId, Authentication authentication) {
        return service.get(authentication.getName(), choirId);
    }

    @PutMapping
    public Mono<SettingView> update(
            @PathVariable long choirId,
            @Valid @RequestBody SeparationThresholdRequest request,
            Authentication authentication) {
        return service.update(
                authentication.getName(), choirId, request.threshold());
    }

    public record SeparationThresholdRequest(
            @NotNull @Positive BigDecimal threshold) {
    }
}
