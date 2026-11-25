package com.rubengarcia.choir.formation.adapter.in.web;

import com.rubengarcia.choir.formation.domain.FormationConstraintValidationResult;
import com.rubengarcia.choir.formation.domain.FormationConstraintViolation;
import com.rubengarcia.choir.formation.domain.LockedPositionViolation;
import com.rubengarcia.choir.formation.domain.MobilityBound;
import com.rubengarcia.choir.formation.domain.MobilityConstraintViolation;
import com.rubengarcia.choir.formation.domain.MobilityNotEvaluableReason;
import com.rubengarcia.choir.formation.domain.ProximityViolation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce las violaciones de dominio a elementos de respuesta HTTP.
 * Solo expone código, identificadores disponibles, motivo y mensaje.
 * Omite los identificadores ausentes y no inventa IDs de posiciones no persistidas.
 */
final class FormationConstraintViolationPresenter {

    private FormationConstraintViolationPresenter() {
    }

    static List<Map<String, Object>> present(FormationConstraintValidationResult result) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (FormationConstraintViolation violation : result.violations()) {
            items.add(toItem(violation));
        }
        return List.copyOf(items);
    }

    private static Map<String, Object> toItem(FormationConstraintViolation violation) {
        if (violation instanceof LockedPositionViolation locked) {
            Map<String, Object> item = start(locked.type());
            put(item, "singerId", locked.singerId());
            item.put("message", "La posición bloqueada del coreuta " + locked.singerId()
                    + " no puede cambiar sus coordenadas.");
            return item;
        }
        if (violation instanceof ProximityViolation proximity) {
            Map<String, Object> item = start(proximity.type());
            put(item, "constraintId", proximity.constraintId());
            put(item, "singerOneId", proximity.singerOneId());
            put(item, "singerTwoId", proximity.singerTwoId());
            item.put("message", "Los coreutas " + proximity.singerOneId() + " y "
                    + proximity.singerTwoId() + " superan la distancia máxima de la restricción "
                    + proximity.constraintId() + ".");
            return item;
        }
        if (violation instanceof MobilityConstraintViolation mobility) {
            Map<String, Object> item = start(mobility.type());
            put(item, "constraintId", mobility.constraintId());
            put(item, "singerId", mobility.singerId());
            put(item, "positionId", mobility.positionId());
            if (mobility.notEvaluableReason() != null) {
                item.put("reason", mobility.notEvaluableReason().name());
                item.put("message", "No se puede evaluar la restricción de movilidad "
                        + mobility.constraintId() + " del coreuta " + mobility.singerId()
                        + ": " + reasonText(mobility.notEvaluableReason()) + ".");
            } else {
                item.put("message", "La posición del coreuta " + mobility.singerId()
                        + " queda fuera de la región de movilidad: "
                        + boundsText(mobility.violatedBounds()) + ".");
            }
            return item;
        }
        Map<String, Object> item = start(violation.type());
        item.put("message", "La restricción de formación no se cumple.");
        return item;
    }

    private static Map<String, Object> start(String code) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        return item;
    }

    private static void put(Map<String, Object> item, String key, Object value) {
        if (value != null) {
            item.put(key, value);
        }
    }

    private static String reasonText(MobilityNotEvaluableReason reason) {
        return switch (reason) {
            case MISSING_DRAFT_POSITION -> "el coreuta no tiene posición en el borrador";
            case AMBIGUOUS_DRAFT_POSITION -> "el coreuta tiene más de una posición en el borrador";
            case MISSING_COORDINATES -> "la posición no tiene coordenadas";
        };
    }

    private static String boundsText(Set<MobilityBound> bounds) {
        List<String> parts = new ArrayList<>();
        for (MobilityBound bound : bounds) {
            parts.add(switch (bound) {
                case X_BELOW_MIN -> "X por debajo del mínimo";
                case X_ABOVE_MAX -> "X por encima del máximo";
                case Y_BELOW_MIN -> "Y por debajo del mínimo";
                case Y_ABOVE_MAX -> "Y por encima del máximo";
            });
        }
        return String.join(", ", parts);
    }
}
