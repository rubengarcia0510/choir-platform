package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.WorkVocalAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/works/{workId}/assignments")
public class WorkVocalAssignmentController {

    private final WorkVocalAssignmentService service;

    public WorkVocalAssignmentController(WorkVocalAssignmentService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<WorkVocalAssignmentService.Assignment> list(
            @PathVariable long choirId,
            @PathVariable long workId,
            Authentication authentication) {
        return service.list(authentication.getName(), choirId, workId);
    }

    @PutMapping("/{singerId}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<WorkVocalAssignmentService.Assignment> assign(
            @PathVariable long choirId,
            @PathVariable long workId,
            @PathVariable long singerId,
            @Valid @RequestBody AssignmentRequest request,
            Authentication authentication) {
        return service.assign(
                authentication.getName(), choirId, workId,
                singerId, request.vocalPlanDivisionId());
    }

    public record AssignmentRequest(@NotNull Long vocalPlanDivisionId) {}
}
