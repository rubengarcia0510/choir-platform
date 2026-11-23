package com.rubengarcia.choir.formation.domain;

import java.time.LocalDateTime;

/**
 * Configuración de visibilidad de un coreuta protegido dentro de una formación.
 *
 * <p>Solo representa qué coreuta debe ser visible y si la restricción está
 * activa. La regla de evaluación no está definida todavía: faltan la orientación
 * escénica, la dirección de visión hacia el público y la regla geométrica de
 * obstrucción. Por eso esta clase no contiene ni calcula esos datos, y no
 * declara ninguna posición como cumplida. Ver
 * {@code docs/decisions/CHOIR-86-tarea-1-modelo-dominio.md}.</p>
 */
public record FormationVisibilityConstraint(
        Long id,
        Long formationId,
        Long protectedSingerId,
        boolean active,
        LocalDateTime createdAt) {

    public FormationVisibilityConstraint {
        if (formationId == null) {
            throw new IllegalArgumentException("formationId es obligatorio");
        }
        if (protectedSingerId == null) {
            throw new IllegalArgumentException("protectedSingerId es obligatorio");
        }
    }
}
