package com.rubengarcia.choir.api;

import com.rubengarcia.choir.service.ConcertPublicationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/choirs/{choirId}/concerts/{concertId}")
public class ConcertPublicationController {

    private final ConcertPublicationService service;

    public ConcertPublicationController(ConcertPublicationService service) {
        this.service = service;
    }

    @PostMapping("/publish")
    public Mono<ConcertPublicationService.Publication> publish(
            @PathVariable long choirId,
            @PathVariable long concertId,
            Authentication authentication) {
        return service.publish(authentication.getName(), choirId, concertId);
    }
}
