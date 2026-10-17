package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.ConcertVocalAdjustmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/choirs/{choirId}/concerts/{concertId}/vocal")
public class ConcertVocalAdjustmentController {

    private final ConcertVocalAdjustmentService service;

    public ConcertVocalAdjustmentController(ConcertVocalAdjustmentService service) {
        this.service = service;
    }

    @GetMapping("/works/{workId}/adjustment")
    public Mono<ConcertVocalAdjustmentService.Adjustment> getAdjustment(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long workId,
            Authentication authentication) {
        return service.getAdjustment(
                authentication.getName(), choirId, concertId, workId);
    }

    @PutMapping("/works/{workId}/adjustment")
    public Mono<ConcertVocalAdjustmentService.Adjustment> saveAdjustment(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long workId,
            @Valid @RequestBody AdjustmentRequest request,
            Authentication authentication) {
        return service.saveAdjustment(
                authentication.getName(), choirId, concertId, workId, request.notes());
    }

    @PutMapping("/works/{workId}/assignments/{singerId}")
    public Mono<ConcertVocalAdjustmentService.Assignment> assignSinger(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long workId,
            @PathVariable long singerId,
            @Valid @RequestBody AssignmentRequest request,
            Authentication authentication) {
        return service.assignSinger(
                authentication.getName(), choirId, concertId, workId,
                singerId, request.vocalPlanDivisionId());
    }

    @GetMapping("/warnings")
    public Mono<List<ConcertVocalAdjustmentService.Warning>> warnings(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.warnings(authentication.getName(), choirId, concertId);
    }

    public record AdjustmentRequest(@Size(max = 2000) String notes) {}

    public record AssignmentRequest(@NotNull Long vocalPlanDivisionId) {}
}
