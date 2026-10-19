package com.rubengarcia.choir.api;

import com.rubengarcia.choir.domain.RepertoireItem;
import com.rubengarcia.choir.service.RepertoireItemService;
import com.rubengarcia.choir.service.RepertoireItemService.RepertoireItemData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/choirs/{choirId}/repertoire")
public class RepertoireItemController {

    private final RepertoireItemService service;

    public RepertoireItemController(RepertoireItemService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<RepertoireItem> list(
            @PathVariable long choirId,
            Authentication authentication) {
        return service.list(authentication.getName(), choirId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RepertoireItem> create(
            @PathVariable long choirId,
            @Valid @RequestBody RepertoireItemRequest request,
            Authentication authentication) {
        return service.create(
                authentication.getName(),
                choirId,
                new RepertoireItemData(
                        request.title(),
                        request.composer(),
                        request.arranger(),
                        request.notes()));
    }

    public record RepertoireItemRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 160) String composer,
            @Size(max = 160) String arranger,
            @Size(max = 2000) String notes) {
    }
}
