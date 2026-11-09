# CHOIR-24 — Modelar y puntuar transiciones entre formaciones

- **Estado:** ⏳ Pendiente (Fase 3 — Cálculo y Optimización)
- **Fuente de verdad:** [CHOIR-24 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-24)
- **Dependencias técnicas previas:** CHOIR-67 (Reglas de escena), CHOIR-106 (Versionado y snapshots inmutables)
- **Desbloquea:** CHOIR-25 (Ranking de alternativas), CHOIR-30 (Modo ensayo)
- **Frontend consumidor:** `choir-platform-web` (Métricas de transición y modo ensayo)

---

## 1. Objetivo y Alcance

Calcular de forma **determinística y reproducible** el coste físico y los riesgos espaciales de una transición entre dos versiones exactas de formaciones (`Version A` $\to$ `Version B`).

### Métricas a Calcular:
1. **Distancia Total:** Suma de las distancias recorridas por todos los integrantes que cambian de lugar.
2. **Cruces de Trayectoria:** Detección de intersecciones de vectores de movimiento entre coreutas que se desplazan simultáneamente.
3. **Simultaneidad y Desplazamientos Críticos:** Número de integrantes en movimiento simultáneo y detección de coreutas que deben recorrer distancias excesivas en un tiempo escénico corto.
4. **Evaluación de Reglas de Escena:** Incorporación de las advertencias derivadas de CHOIR-67.

---

## 2. Contrato HTTP

* `POST /api/choirs/{choirId}/concerts/{concertId}/transitions/evaluate`
* Payload:
```json
{
  "fromVersionId": 101,
  "toVersionId": 102,
  "repertoireItemId": 45
}
```
* Respuesta (`200 OK`):
```json
{
  "totalDistance": 42.6,
  "maxIndividualDistance": 5.2,
  "crossingCount": 2,
  "movingSingersCount": 8,
  "warnings": [
    {
      "code": "CROSSING_DETECTED",
      "singers": [12, 19],
      "message": "Cruce directo entre Pedro y Sofía en el sector central"
    }
  ],
  "score": 82.5
}
```

Los identificadores de versión son numéricos (`Long`) en el contrato de borradores existente. Confirmar la semántica y las rutas de transición en Jira antes de implementar este endpoint propuesto.

---

## 3. Criterios de Aceptación

- [ ] Dados los mismos snapshots de entrada, el resultado de métricas y score es idéntico (reproducibilidad).
- [ ] Calcula distancias euclidianas exactas en el plano del escenario.
- [ ] Detecta intersecciones geométricas entre segmentos de desplazamiento.
- [ ] No muta ninguna formación ni versión.

---

## Límites entre dominios

- `formation` mantiene posiciones, bloqueos y restricciones de proximidad, movilidad y visibilidad.
- `transition` compara snapshots, calcula distancias y detecta cruces y conflictos temporales.
- `scene` mantiene las reglas especiales de escena de CHOIR-20/67.
- CHOIR-121/122 no importan validadores ni clases internas de `formation`.
- CHOIR-123 integra los cálculos de transición con las reglas de `scene`.
- CHOIR-24 no incorpora el motor de restricciones de formación.
- Una integración futura con restricciones de formación requiere alcance y contrato explícitos.

Consultar `docs/architecture/domain-boundaries.md`.
