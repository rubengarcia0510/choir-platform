package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.VocalPlanDivision;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface VocalPlanDivisionRepository
        extends ReactiveCrudRepository<VocalPlanDivision, Long> {

    Flux<VocalPlanDivision> findByVocalPlanIdOrderByVocalSectionIdAscDivisionNumberAsc(
            Long vocalPlanId);

    Flux<VocalPlanDivision> findByVocalPlanIdAndVocalSectionIdOrderByDivisionNumberAsc(
            Long vocalPlanId, Long vocalSectionId);
}
