package com.rubengarcia.choir.formation.adapter.out.authorization;

import com.rubengarcia.choir.formation.application.port.out.FormationVersionLifecycleAuthorizationPort;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FormationVersionLifecycleAuthorizationAdapter
        implements FormationVersionLifecycleAuthorizationPort {

    private final ResourceAuthorizationService resourceAuthorizationService;

    public FormationVersionLifecycleAuthorizationAdapter(
            ResourceAuthorizationService resourceAuthorizationService) {
        this.resourceAuthorizationService = resourceAuthorizationService;
    }

    @Override
    public Mono<Void> requireOwnedChoir(String email, long choirId) {
        return resourceAuthorizationService.requireOwnedChoir(email, choirId);
    }
}
