package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.VocalPlanValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/works/{workId}/vocal-plan/validation")
public class VocalPlanValidationController {

    private final VocalPlanValidationService service;

    public VocalPlanValidationController(VocalPlanValidationService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<VocalPlanValidationService.ValidationReport> inspect(
            @PathVariable long choirId,
            @PathVariable long workId,
            Authentication authentication) {
        return service.inspect(authentication.getName(), choirId, workId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public Mono<VocalPlanValidationService.ValidationReport> validate(
            @PathVariable long choirId,
            @PathVariable long workId,
            Authentication authentication) {
        return service.validate(authentication.getName(), choirId, workId);
    }
}
