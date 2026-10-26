package com.rubengarcia.choir.scene.application.usecase;

import java.math.BigDecimal;
import reactor.core.publisher.Mono;

public interface SceneRuleSeparationSettingsService {

    Mono<SettingView> get(String email, long choirId);

    Mono<SettingView> update(String email, long choirId, BigDecimal threshold);

    record SettingView(long revision, BigDecimal threshold) {
    }
}
