package com.rubengarcia.choir.formation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Valida que las posiciones bloqueadas (locked = true) no cambien sus coordenadas.
 *
 * <p>Compara por singerId, igual que la persistencia existente. Para cada posición
 * propuesta cuyo coreuta está bloqueado en el estado persistido:</p>
 * <ul>
 *   <li>si X e Y coinciden exactamente (comparación numérica, sin importar la escala),
 *       la posición se acepta y conserva locked = true;</li>
 *   <li>si X o Y cambian, se registra una violación;</li>
 *   <li>si la propuesta no tiene X o Y, no puede demostrarse que no cambió: es violación.</li>
 * </ul>
 * <p>Un coreuta bloqueado omitido de la propuesta no es violación: quitarlo de la formación
 * está permitido. Las posiciones no bloqueadas pueden cambiar. No modifica sus entradas.</p>
 */
public final class LockedPositionValidator {

    private LockedPositionValidator() {
    }

    public static LockedPositionValidationResult validate(
            List<FormationPosition> persisted,
            List<FormationPosition> proposed) {
        Objects.requireNonNull(persisted, "persisted es obligatorio");
        Objects.requireNonNull(proposed, "proposed es obligatorio");

        Map<Long, FormationPosition> persistedBySinger = persisted.stream()
                .collect(Collectors.toMap(FormationPosition::getSingerId, position -> position));

        List<LockedPositionViolation> violations = new ArrayList<>();
        List<FormationPosition> validated = new ArrayList<>();

        for (FormationPosition candidate : proposed) {
            FormationPosition current = persistedBySinger.get(candidate.getSingerId());
            boolean locked = candidate.isLocked();

            if (current != null && current.isLocked()) {
                requireCoordinates(current);
                if (sameCoordinates(current, candidate)) {
                    locked = true;
                } else {
                    violations.add(new LockedPositionViolation(
                            candidate.getSingerId(),
                            current.getXCoordinate(),
                            current.getYCoordinate(),
                            candidate.getXCoordinate(),
                            candidate.getYCoordinate()));
                }
            }

            validated.add(copy(candidate, locked));
        }

        if (!violations.isEmpty()) {
            return new LockedPositionValidationResult(violations, List.of());
        }
        return new LockedPositionValidationResult(List.of(), validated);
    }

    private static void requireCoordinates(FormationPosition persisted) {
        if (persisted.getXCoordinate() == null || persisted.getYCoordinate() == null) {
            throw new IllegalArgumentException(
                    "La posición bloqueada persistida no tiene coordenadas");
        }
    }

    private static boolean sameCoordinates(FormationPosition persisted, FormationPosition candidate) {
        BigDecimal x = candidate.getXCoordinate();
        BigDecimal y = candidate.getYCoordinate();
        if (x == null || y == null) {
            return false;
        }
        return persisted.getXCoordinate().compareTo(x) == 0
                && persisted.getYCoordinate().compareTo(y) == 0;
    }

    /** Copia independiente de la posición, con el estado de bloqueo indicado. */
    private static FormationPosition copy(FormationPosition source, boolean locked) {
        FormationPosition copy = new FormationPosition();
        copy.setId(source.getId());
        copy.setFormationVersionId(source.getFormationVersionId());
        copy.setSingerId(source.getSingerId());
        copy.setSingerNameSnapshot(source.getSingerNameSnapshot());
        copy.setVoicePartSnapshot(source.getVoicePartSnapshot());
        copy.setVoiceSectionIdSnapshot(source.getVoiceSectionIdSnapshot());
        copy.setVoiceSectionNameSnapshot(source.getVoiceSectionNameSnapshot());
        copy.setDivisionNumberSnapshot(source.getDivisionNumberSnapshot());
        copy.setHeightCmSnapshot(source.getHeightCmSnapshot());
        copy.setPositionLabel(source.getPositionLabel());
        copy.setXCoordinate(source.getXCoordinate());
        copy.setYCoordinate(source.getYCoordinate());
        copy.setLocked(locked);
        copy.setCreatedAt(source.getCreatedAt());
        return copy;
    }
}
