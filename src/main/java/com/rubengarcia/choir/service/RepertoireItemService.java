package com.rubengarcia.choir.service;

import com.rubengarcia.choir.domain.RepertoireItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RepertoireItemService {

    Flux<RepertoireItem> list(String email, long choirId);

    Mono<RepertoireItem> create(
            String email,
            long choirId,
            RepertoireItemData data);

    record RepertoireItemData(
            String title,
            String composer,
            String arranger,
            String notes) {
    }
}
