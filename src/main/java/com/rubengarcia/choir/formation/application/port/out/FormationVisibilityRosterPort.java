package com.rubengarcia.choir.formation.application.port.out;

import reactor.core.publisher.Flux;

/**
 * Roster de coreutas asignados a una obra en un concierto, para evaluar visibilidad.
 * Lee las asignaciones vigentes, independientemente de que el concierto esté publicado.
 */
public interface FormationVisibilityRosterPort {

    /**
     * IDs distintos de coreutas activos asignados en concert_vocal_assignments para el
     * coro, concierto y elemento de repertorio indicados.
     */
    Flux<Long> findActiveAssignedSingerIds(
            long choirId,
            long concertId,
            long repertoireItemId);
}
