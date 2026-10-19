package com.rubengarcia.choir.repository;

import com.rubengarcia.choir.domain.Concert;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ConcertRepository extends ReactiveCrudRepository<Concert, Long> {

    Flux<Concert> findByChoirId(Long choirId);

    @Modifying
    @Query("""
            UPDATE concerts
            SET title = :title,
                concert_date = :concertDate,
                venue = :venue,
                status = :newStatus
            WHERE id = :concertId
              AND choir_id = :choirId
              AND status = :expectedStatus
            """)
    Mono<Integer> updateIfStatus(
            @Param("choirId") Long choirId,
            @Param("concertId") Long concertId,
            @Param("expectedStatus") String expectedStatus,
            @Param("title") String title,
            @Param("concertDate") LocalDate concertDate,
            @Param("venue") String venue,
            @Param("newStatus") String newStatus);

    @Modifying
    @Query("""
            UPDATE concerts
            SET status = 'ARCHIVED'
            WHERE id = :concertId
              AND choir_id = :choirId
              AND status = :expectedStatus
            """)
    Mono<Integer> archiveIfStatus(
            @Param("choirId") Long choirId,
            @Param("concertId") Long concertId,
            @Param("expectedStatus") String expectedStatus);
}
