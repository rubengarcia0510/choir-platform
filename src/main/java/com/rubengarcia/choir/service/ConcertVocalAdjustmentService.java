package com.rubengarcia.choir.service;

import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.List;

public interface ConcertVocalAdjustmentService {

    public record Adjustment(
            Long id,
            Long concertId,
            Long workId,
            Long choirId,
            String notes,
            LocalDateTime createdAt) {}

    public record Assignment(
            Long id,
            Long concertId,
            Long workId,
            Long singerId,
            Long vocalPlanDivisionId,
            Long choirId,
            LocalDateTime createdAt) {}

    public record Warning(
            String code,
            Long workId,
            Long singerId,
            Long divisionId,
            String message) {}

    Mono<Adjustment> getAdjustment(
            String email, long choirId, long concertId, long workId);

    Mono<Adjustment> saveAdjustment(
            String email, long choirId, long concertId, long workId, String notes);

    Mono<Assignment> assignSinger(
            String email, long choirId, long concertId, long workId,
            long singerId, long divisionId);

    Mono<List<Warning>> warnings(
            String email, long choirId, long concertId);
}
