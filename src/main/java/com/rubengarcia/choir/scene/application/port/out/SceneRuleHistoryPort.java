package com.rubengarcia.choir.scene.application.port.out;

import com.rubengarcia.choir.scene.domain.SceneRuleRevisionSnapshot;
import reactor.core.publisher.Flux;

public interface SceneRuleHistoryPort {

    Flux<SceneRuleRevisionSnapshot> findAllRevisionsByRepertoireItemId(
            long repertoireItemId);
}
