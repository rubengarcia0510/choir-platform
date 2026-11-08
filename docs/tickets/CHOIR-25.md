# CHOIR-25 — Generar y rankear alternativas de transición

- **Estado:** ⏳ Pendiente (Fase 3 — Cálculo y Optimización)
- **Fuente de verdad:** [CHOIR-25 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-25)
- **Dependencias técnicas previas:** CHOIR-24 (Puntuación), CHOIR-23 (Decisiones bloqueadas)
- **Desbloquea:** CHOIR-26 (Preferencias del director), CHOIR-28 (Ausencias)
- **Frontend consumidor:** `choir-platform-web` (Selector de alternativas)

---

## 1. Objetivo y Alcance

A partir de una formación inicial fija y una formación objetivo, generar un conjunto de **alternativas viables de asignación y trayectoria** y ordenarlas por calidad escénica (menor distancia global, cero o mínimos cruces, máxima fluidez escénica).

---

## 2. Contrato HTTP

* `POST /api/choirs/{choirId}/concerts/{concertId}/transitions/alternatives`
* Payload:
```json
{
  "fromVersionId": 101,
  "targetFormationId": 55,
  "repertoireItemId": 45
}
```
* Respuesta: Lista de alternativas ordenadas por score de calidad, incluyendo matriz de asignación coreuta-coordenada y métricas comparables.

`fromVersionId` corresponde a un identificador numérico (`Long`) en el contrato de borradores existente. El formato completo de las alternativas y la operación para seleccionar/aplicar una alternativa quedan pendientes de confirmar en el contrato backend/Jira.

---

## 3. Criterios de Aceptación

- [ ] Genera al menos una alternativa cuando exista solución viable.
- [ ] No altera ninguna posición con `locked = true`.
- [ ] Presenta métricas cuantitativas homogéneas para permitir la comparación objetiva.
- [ ] El director puede seleccionar una alternativa para convertirla en el borrador de trabajo sin alterar formaciones originales.
