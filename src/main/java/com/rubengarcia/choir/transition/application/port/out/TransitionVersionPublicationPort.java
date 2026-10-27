package com.rubengarcia.choir.transition.application.port.out;

import com.rubengarcia.choir.domain.TransitionVersion;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
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
                    : Collections.unmodifiableList(new ArrayList<>(acceptedWarnings));
        }
    }

    record WarningAcceptance(
            int resultOrder,
            int warningOrder,
            String reason) {
    }
}
