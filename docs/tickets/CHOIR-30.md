# CHOIR-30 — Modo ensayo y reproducción de transiciones

- **Estado:** ⏳ Pendiente (Fase 5 — Ensayo y Visualización)
- **Fuente de verdad:** [CHOIR-30 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-30)
- **Dependencia técnica previa:** CHOIR-24 (Puntuación y trayectorias de transición)
- **Desbloquea:** CHOIR-32 (Vista de coreuta)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-30-FRONTEND — Reproductor interactivo)

---

## 1. Objetivo y Alcance

Permitir al director y al elenco **ensayar visualmente las transiciones** entre canciones de un concierto.

El backend provee un endpoint que calcula y descompone la transición en **pasos cinemáticos / etapas de interpolación discretas** (`steps`), resolviendo qué coreutas inician su movimiento en cada momento, a qué velocidad y en qué orden según las reglas especiales (CHOIR-20 y CHOIR-67).

---

## 2. Contrato HTTP

* `GET /api/choirs/{choirId}/concerts/{concertId}/transitions/{transitionId}/playback-steps`
* Respuesta:
```json
{
  "totalDurationSeconds": 15,
  "steps": [
    {
      "stepIndex": 0,
      "timestamp": 0.0,
      "positions": [
        { "singerId": 12, "x": 2.0, "y": 1.0, "state": "STATIONARY" }
      ]
    },
    {
      "stepIndex": 1,
      "timestamp": 3.5,
      "positions": [
        { "singerId": 12, "x": 2.8, "y": 1.4, "state": "MOVING" }
      ]
    }
  ],
  "potentialCollisions": []
}
```

---

## 3. Criterios de Aceptación

- [ ] Devuelve la secuencia temporal completa para permitir animación fluida o paso a paso en frontend.
- [ ] Respeta reglas de tandas y salidas escalonadas.
- [ ] No requiere cálculo matemático complejo en el cliente móvil o navegador web.
