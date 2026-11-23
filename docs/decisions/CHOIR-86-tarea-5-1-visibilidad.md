# CHOIR-86 — Tarea 5.1: evaluador de visibilidad

## Estado

Evaluador de dominio puro implementado en `feature/CHOIR-86-visibility-evaluation`,
**no integrado** en el composite de validación (CHOIR-87) ni expuesto por API.
Esta tarea sustituye la decisión previa de «visibilidad fuera de alcance» por el
contrato funcional aprobado a continuación. No declarar la visibilidad validada en
pantallas reales ni con datos de producción.

## Contrato aprobado

Para cada restricción de visibilidad activa:

1. Se localiza la posición del `protectedSingerId` en el DRAFT.
2. Se compara con todos los demás coreutas del mismo DRAFT.
3. Un coreuta es posible obstructor si:
   - `|X_obstructor - X_protegido| <= 2` (tolerancia fija, inclusiva);
   - `Y_obstructor > Y_protegido` (delante, con y = 100 en el frente);
   - `heightCmSnapshot_obstructor > heightCmSnapshot_protegido` (estricto).
4. Cada obstructor que cumple las tres condiciones produce un `VisibilityFinding`.
5. La altura usada es siempre `heightCmSnapshot`, nunca la altura del perfil.

## Datos insuficientes

Nunca se asume visibilidad garantizada. Motivos de no evaluable:
`MISSING_PROTECTED_DRAFT_POSITION`, `AMBIGUOUS_PROTECTED_DRAFT_POSITION`,
`MISSING_PROTECTED_COORDINATES`, `MISSING_PROTECTED_HEIGHT`,
`MISSING_OBSTRUCTOR_COORDINATES`, `MISSING_OBSTRUCTOR_HEIGHT` y
`AMBIGUOUS_OBSTRUCTOR_DRAFT_POSITION`.

Un obstructor sin altura no impide una evaluación completa si está fuera de la
tolerancia horizontal o detrás del protegido: en esos casos no puede obstruir.

## Limitaciones conocidas

- Un coreuta con más de una posición en el DRAFT se marca no evaluable aunque esté
  lejos. Es conservador y evita elegir una posición arbitraria.
- No hay simulación de línea visual, geometría corporal, orientación adicional ni
  altura inferida.
- La tolerancia de 2 y la regla de altura estricta son decisiones funcionales,
  no validadas con datos reales del escenario.
