package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.SceneRuleService;
import com.rubengarcia.choir.service.SceneRuleService.GroupData;
import com.rubengarcia.choir.service.SceneRuleService.RuleData;
import com.rubengarcia.choir.service.SceneRuleService.RuleView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(
        "/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/scene-rules")
public class SceneRuleController {

    private final SceneRuleService service;

    public SceneRuleController(SceneRuleService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<RuleView> list(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long repertoireItemId,
            Authentication authentication) {
        return service.list(
                authentication.getName(), choirId, concertId, repertoireItemId);
    }

    @GetMapping("/{ruleId}")
    public Mono<RuleView> get(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long repertoireItemId,
            @PathVariable long ruleId,
            Authentication authentication) {
        return service.get(
                authentication.getName(),
                choirId,
                concertId,
                repertoireItemId,
                ruleId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RuleView> create(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long repertoireItemId,
            @Valid @RequestBody RuleRequest request,
            Authentication authentication) {
        return service.create(
                authentication.getName(),
                choirId,
                concertId,
                repertoireItemId,
                request.toData());
    }

    @PutMapping("/{ruleId}")
    public Mono<RuleView> update(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long repertoireItemId,
            @PathVariable long ruleId,
            @Valid @RequestBody RuleRequest request,
            Authentication authentication) {
        return service.update(
                authentication.getName(),
                choirId,
                concertId,
                repertoireItemId,
                ruleId,
                request.toData());
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> reorder(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long repertoireItemId,
            @Valid @RequestBody RuleOrderRequest request,
            Authentication authentication) {
        return service.reorder(
                authentication.getName(),
                choirId,
                concertId,
                repertoireItemId,
                request.ruleIds());
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deactivate(
            @PathVariable long choirId,
            @PathVariable long concertId,
            @PathVariable long repertoireItemId,
            @PathVariable long ruleId,
            Authentication authentication) {
        return service.deactivate(
                authentication.getName(),
                choirId,
                concertId,
                repertoireItemId,
                ruleId);
    }

    public record RuleRequest(
            @NotBlank @Size(max = 30) String ruleType,
            @NotBlank @Size(max = 1000) String description,
            @NotNull List<@NotNull @Valid GroupRequest> groups) {

        RuleData toData() {
            return new RuleData(
                    ruleType,
                    description,
                    groups.stream()
                            .map(group -> new GroupData(
                                    group.label(),
                                    group.compositionMode() == null
                                            ? "COUNT"
                                            : group.compositionMode(),
                                    group.memberCount(),
                                    group.singerIds() == null
                                            ? List.of()
                                            : group.singerIds()))
                            .toList());
        }
    }

    public record GroupRequest(
            @NotBlank @Size(max = 100) String label,
            String compositionMode,
            @Positive Integer memberCount,
            List<@NotNull @Positive Long> singerIds) {
    }

    public record RuleOrderRequest(
            @NotNull List<@NotNull Long> ruleIds) {
    }
}
