package com.rubengarcia.choir.scene.application.usecase;

import com.rubengarcia.choir.service.ResourceAuthorizationService;
import com.rubengarcia.choir.transition.application.port.out.SceneRuleSeparationSettingsPort;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class SceneRuleSeparationSettingsServiceImpl
        implements SceneRuleSeparationSettingsService {

    private static final BigDecimal MAX_SQUARED_DISTANCE = BigDecimal.valueOf(2);

    private final ResourceAuthorizationService authorizationService;
    private final SceneRuleSeparationSettingsPort settingsPort;

    public SceneRuleSeparationSettingsServiceImpl(
            ResourceAuthorizationService authorizationService,
            SceneRuleSeparationSettingsPort settingsPort) {
        this.authorizationService = authorizationService;
        this.settingsPort = settingsPort;
    }

    @Override
    public Mono<SettingView> get(String email, long choirId) {
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(settingsPort.getCurrent())
                .map(setting -> new SettingView(
                        setting.revision(), setting.threshold()));
    }

    @Override
    public Mono<SettingView> update(
            String email, long choirId, BigDecimal threshold) {
        if (!validThreshold(threshold)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El umbral debe ser positivo y no superar la diagonal normalizada"));
        }
        return authorizationService.requireOwnedChoir(email, choirId)
                .then(settingsPort.updateCurrent(threshold, email))
                .map(setting -> new SettingView(
                        setting.revision(), setting.threshold()));
    }

    private boolean validThreshold(BigDecimal threshold) {
        return threshold != null
                && threshold.signum() > 0
                && threshold.multiply(threshold)
                        .compareTo(MAX_SQUARED_DISTANCE) <= 0;
    }
}
