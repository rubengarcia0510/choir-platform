package com.rubengarcia.choir.formation.application.exception;

/**
 * La restricción física entra en conflicto con una existente (por ejemplo, una
 * segunda restricción de movilidad para el mismo coreuta en la formación).
 * No expone detalles de persistencia.
 */
public class PhysicalConstraintConflictException extends RuntimeException {

    public PhysicalConstraintConflictException() {
        super("Ya existe una restricción de movilidad para este coreuta en la formación");
    }
}
