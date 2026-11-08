# CHOIR-67 — Integrar reglas especiales en la planificación de transiciones

- **Estado:** ⏳ Pendiente (Fase 2 — Reglas Especiales de Escena)
- **Fuente de verdad:** [CHOIR-67 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-67)
- **Dependencias técnicas previas:** CHOIR-20 (Reglas por canción), CHOIR-68 (Regresiones). CHOIR-97 figura como dependencia en la planificación, pero no tiene especificación local; verificar su existencia y alcance en Jira antes de considerarlo bloqueante.
- **Desbloquea:** CHOIR-24 (Puntuación y cálculo de transiciones)
- **Frontend consumidor:** `choir-platform-web` (Gestión de advertencias y aceptación de transiciones)

---

## 1. Objetivo y Alcance

Integrar el motor de reglas especiales de escena (CHOIR-20) en el proceso de **cálculo, recálculo y validación de transiciones escénicas** entre versiones exactas de formaciones (`fromFormationVersionId` $\to$ `toFormationVersionId`).

Cuando una regla especial no pueda satisfacerse (por ejemplo, una salida en tanda requiere 4 coreutas pero la disposición deja solo 3 disponibles sin cruce peligroso), el sistema **no debe fallar silenciosamente ni bloquear automáticamente**, sino emitir una **advertencia estructurada** (`warning`) requiriendo una decisión explícita del director antes de permitir la publicación.

---

## 2. Flujo y Criterios de Aceptación

1. **Contexto Exacto:** Se valida que la obra pertenezca al concierto y que las formaciones de origen y destino correspondan a versiones publicadas/congeladas.
2. **Evaluación Ordenada:** Las reglas activas se evalúan respetando estrictamente su `sort_order`.
3. **Advertencias Estructuradas:** Si una regla no se cumple, se genera un registro inmutable de advertencia vinculando el ID de la regla y el motivo.
4. **Decisiones del Director:**
   * **Aceptar advertencia:** El director asume el riesgo escénico $\to$ permite publicar.
   * **Revisar / Descartar:** Impide la publicación hasta que se modifique la formación o la regla.
5. **Auditoría e Inmutabilidad:** Toda evaluación y decisión queda registrada en las tablas de auditoría de transiciones sin posibilidad de borrado (`DELETE`).
