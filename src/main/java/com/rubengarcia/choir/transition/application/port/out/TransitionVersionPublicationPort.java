package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.domain.TransitionVersion;
import java.util.List;
import reactor.core.publisher.Mono;

public interface TransitionVersionPublicationPort {

    Mono<TransitionVersion> publish(PublicationCommand command);

    record PublicationCommand(
            long choirId,
            long transitionId,
            long transitionVersionId,
            long evaluationId,
            String directorEmail,
            List<WarningAcceptance> acceptedWarnings) {

        public PublicationCommand {
            acceptedWarnings = acceptedWarnings == null
                    ? List.of()
                    : List.copyOf(acceptedWarnings);
        }
    }

    record WarningAcceptance(
            int resultOrder,
            int warningOrder,
            String reason) {
    }
}
