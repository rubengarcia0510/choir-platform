# CHOIR-23 — Bloquear decisiones artísticas y definir restricciones en optimizador

- **Estado:** ⏳ Pendiente (Fase 3 — Cálculo y Optimización)
- **Fuente de verdad:** [CHOIR-23 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-23)
- **Dependencias técnicas previas:** CHOIR-84 (Bloqueos), CHOIR-87 (Validación de restricciones), CHOIR-88
- **Desbloquea:** CHOIR-25 (Ranking de alternativas), CHOIR-28 (Ausencias)
- **Frontend consumidor:** `choir-platform-web` (Garantía de inmutabilidad en optimizaciones)

---

## 1. Objetivo y Alcance

Garantizar que el motor de optimización, sugerencia de transiciones y recálculo escénico **respete como restricciones duras (hard constraints)**:
1. Las posiciones marcadas explícitamente como bloqueadas (`locked = true`).
2. Las restricciones de proximidad (CHOIR-85), movilidad y visibilidad (CHOIR-86).

El optimizador nunca debe alterar una posición bloqueada ni proponer alternativas que violen restricciones activas, asegurando que la soberanía artística del director quede preservada en todo momento.

---

## 2. Criterios de Aceptación

- [ ] Las posiciones bloqueadas no son consideradas como variables de reasignación en el espacio de búsqueda.
- [ ] Cualquier solución candidata generada por el optimizador pasa obligatoriamente por el pipeline de validación de CHOIR-87.
- [ ] Si no existe una solución matemática que respete todas las decisiones bloqueadas, el sistema no fuerza cambios sobre las posiciones bloqueadas, sino que informa la incompatibilidad al director.
