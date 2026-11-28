package com.rubengarcia.choir.scene.adapter.out.persistence;

import com.rubengarcia.choir.scene.application.exception.StageAccessDeniedException;
import com.rubengarcia.choir.scene.application.port.out.StageAuthorizationPort;
import com.rubengarcia.choir.service.ResourceAuthorizationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Autorización de escenarios. La propiedad se decide en {@link ResourceAuthorizationService}, que no cambia.
 * Cuando ese servicio rechaza con 404, este adaptador distingue dos casos mediante una consulta de
 * existencia: si el recurso existe pero pertenece a otro director, responde con {@link StageAccessDeniedException}
 * (403); si no existe, conserva el 404.
 */
@Component
public class StageAuthorizationAdapter implements StageAuthorizationPort {

    private final ResourceAuthorizationService resourceAuthorizationService;
    private final DatabaseClient databaseClient;

    public StageAuthorizationAdapter(
            ResourceAuthorizationService resourceAuthorizationService,
            DatabaseClient databaseClient) {
        this.resourceAuthorizationService = resourceAuthorizationService;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> requireOwnedChoir(String directorEmail, long choirId) {
        return resourceAuthorizationService.requireOwnedChoir(directorEmail, choirId)
                .onErrorResume(ResponseStatusException.class,
                        error -> denied(error, exists("SELECT COUNT(*) FROM choirs WHERE id = :choirId",
                                Map.of("choirId", choirId))));
    }

    @Override
    public Mono<Void> requireOwnedOccurrence(
            String directorEmail, long choirId, long concertId, long repertoireItemId) {
        return resourceAuthorizationService.requireOwnedConcertWork(
                        directorEmail, choirId, concertId, repertoireItemId)
                .onErrorResume(ResponseStatusException.class,
                        error -> denied(error, occurrenceExists(choirId, concertId, repertoireItemId)));
    }

    private Mono<Void> denied(ResponseStatusException error, Mono<Boolean> exists) {
        if (error.getStatusCode().value() != HttpStatus.NOT_FOUND.value()) {
            return Mono.error(error);
        }
        return exists.flatMap(found -> found
                ? Mono.<Void>error(new StageAccessDeniedException())
                : Mono.<Void>error(error));
    }

    private Mono<Boolean> occurrenceExists(long choirId, long concertId, long repertoireItemId) {
        return Mono.zip(
                        exists("SELECT COUNT(*) FROM choirs WHERE id = :choirId",
                                Map.of("choirId", choirId)),
                        exists("SELECT COUNT(*) FROM concerts WHERE id = :concertId AND choir_id = :choirId",
                                Map.of("concertId", concertId, "choirId", choirId)),
                        exists("SELECT COUNT(*) FROM repertoire_items WHERE id = :workId AND choir_id = :choirId",
                                Map.of("workId", repertoireItemId, "choirId", choirId)),
                        exists("SELECT COUNT(*) FROM concert_repertoire WHERE concert_id = :concertId"
                                        + " AND repertoire_item_id = :workId",
                                Map.of("concertId", concertId, "workId", repertoireItemId)))
                .map(all -> all.getT1() && all.getT2() && all.getT3() && all.getT4());
    }

    private Mono<Boolean> exists(String sql, Map<String, Long> parameters) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, Long> parameter : parameters.entrySet()) {
            spec = spec.bind(parameter.getKey(), parameter.getValue());
        }
        return spec.map((row, meta) -> row.get(0, Long.class))
                .one()
                .map(count -> count > 0);
    }
}
