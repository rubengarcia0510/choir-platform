package com.rubengarcia.choir.transition.application.port.out;

import java.math.BigDecimal;
import reactor.core.publisher.Mono;

public interface SceneRuleSeparationSettingsPort {

    Mono<Setting> getCurrent();

    Mono<Setting> updateCurrent(BigDecimal threshold, String changedByEmail);

    record Setting(long revision, BigDecimal threshold) {
    }
}
