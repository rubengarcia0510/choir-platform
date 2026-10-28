package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.TransitionVersion;
import com.rubengarcia.choir.formation.application.usecase.FormationPlanningAuthorizationService;
import com.rubengarcia.choir.transition.application.model.CountGroupAssignment;
import com.rubengarcia.choir.transition.application.port.out.TransitionVersionPublicationPort.WarningAcceptance;
import com.rubengarcia.choir.transition.application.usecase.TransitionVersionLifecycleService;
import com.rubengarcia.choir.transition.domain.TransitionRuleEvaluationSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/transitions/{transitionId}/versions")
public class TransitionVersionController {

    private final TransitionVersionLifecycleService service;
    private final FormationPlanningAuthorizationService planningService;

    public TransitionVersionController(
            TransitionVersionLifecycleService service,
            FormationPlanningAuthorizationService planningService) {
        this.service = service;
        this.planningService = planningService;
    }

    @GetMapping
    public Flux<TransitionVersion> list(
            @PathVariable long choirId,
            @PathVariable long transitionId,
            Authentication authentication) {
        return service.list(authentication.getName(), choirId, transitionId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TransitionVersion> createDraft(
            @PathVariable long choirId,
            @PathVariable long transitionId,
            @Valid @RequestBody CreateDraftRequest request,
            Authentication authentication) {
        return service.createDraft(
                authentication.getName(),
                choirId,
                transitionId,
                request.fromFormationVersionId(),
                request.toFormationVersionId(),
                request.instructions());
    }

    @PostMapping("/{transitionVersionId}/rule-evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TransitionRuleEvaluationSnapshot> evaluateRules(
            @PathVariable long choirId,
            @PathVariable long transitionId,
            @PathVariable long transitionVersionId,
            @Valid @RequestBody(required = false) RuleEvaluationRequest request,
            Authentication authentication) {
        List<CountGroupAssignment> assignments =
                request == null || request.assignments() == null
                        ? List.of()
                        : request.assignments();

        return planningService.evaluateTransitionVersion(
                authentication.getName(),
                choirId,
                transitionId,
                transitionVersionId,
                assignments);
    }

    @GetMapping("/{transitionVersionId}/rule-evaluations")
    public Flux<TransitionRuleEvaluationSnapshot> ruleEvaluationHistory(
            @PathVariable long choirId,
            @PathVariable long transitionId,
            @PathVariable long transitionVersionId,
            Authentication authentication) {
        return planningService.transitionRuleEvaluationHistory(
                authentication.getName(),
                choirId,
                transitionId,
                transitionVersionId);
    }

    @PostMapping("/{transitionVersionId}/publication")
    public Mono<TransitionVersion> publish(
            @PathVariable long choirId,
            @PathVariable long transitionId,
            @PathVariable long transitionVersionId,
            @Valid @RequestBody PublishRequest request,
            Authentication authentication) {
        return service.publish(
                authentication.getName(),
                choirId,
                transitionId,
                transitionVersionId,
                request.evaluationId(),
                request.acceptedWarningCommands());
    }

    public record CreateDraftRequest(
            @NotNull @Positive Long fromFormationVersionId,
            @NotNull @Positive Long toFormationVersionId,
            @Size(max = 4000) String instructions) {
    }

    public record RuleEvaluationRequest(
            @Size(max = 1000) List<@NotNull @Valid CountGroupAssignment> assignments) {
    }

    public record PublishRequest(
            @NotNull @Positive Long evaluationId,
            @Size(max = 100)
            List<@Valid WarningAcceptanceRequest> acceptedWarnings) {

        public List<WarningAcceptance> acceptedWarningCommands() {
            if (acceptedWarnings == null) {
                return List.of();
            }
            return acceptedWarnings.stream()
                    .map(warning -> new WarningAcceptance(
                            warning.resultOrder(),
                            warning.warningOrder(),
                            warning.reason()))
                    .toList();
        }
    }

    public record WarningAcceptanceRequest(
            int resultOrder,
            @PositiveOrZero int warningOrder,
            @NotBlank @Size(max = 1000) String reason) {
    }
}
