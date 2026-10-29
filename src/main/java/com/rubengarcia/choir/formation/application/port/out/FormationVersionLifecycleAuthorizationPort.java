package com.rubengarcia.choir.formation.application.port.out;

import reactor.core.publisher.Mono;

public interface FormationVersionLifecycleAuthorizationPort {

    Mono<Void> requireOwnedChoir(String email, long choirId);
}
