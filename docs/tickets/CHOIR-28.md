# CHOIR-28 — Recalcular formaciones y transiciones ante ausencias

- **Estado:** ⏳ Pendiente (Fase 4 — Ausencias y Adaptación)
- **Fuente de verdad:** [CHOIR-28 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-28)
- **Dependencias técnicas previas:** CHOIR-23 (Decisiones bloqueadas), CHOIR-24 (Puntuación), CHOIR-25 (Alternativas)
- **Desbloquea:** CHOIR-29 (Comparador y aplicación de adaptación)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-29-FRONTEND)

---

## 1. Objetivo y Alcance

Permitir al director registrar la **ausencia o baja de uno o más coreutas** para un concierto u obra, y recalcular automáticamente las formaciones y transiciones afectadas sin alterar las decisiones bloqueadas de los coreutas presentes.

### Reglas de Negocio:
1. Las personas marcadas como ausentes se excluyen de la formación y de los payloads de transición.
2. Las posiciones bloqueadas de coreutas presentes en la propuesta permanecen en sus coordenadas originales; la optimización no las mueve. Si un coreuta ausente tenía una posición bloqueada, su posición se excluye por ausencia y no se reutiliza como una posición activa.
3. Las posiciones vacías dejadas por ausencias pueden cubrirse o compactarse mediante la propuesta del optimizador, respetando todas las posiciones bloqueadas de integrantes presentes.

---

## 2. Contrato HTTP

* `POST /api/choirs/{choirId}/concerts/{concertId}/adaptations/absence/calculate`
* Payload:
```json
{
  "absentSingerIds": [14, 22]
}
```
* Respuesta: Propuesta de reubicación con lista de formaciones afectadas, nuevas coordenadas sugeridas y métricas de impacto escénico.

---

## 3. Criterios de Aceptación

- [ ] Las personas ausentes quedan excluidas de los cálculos.
- [ ] No se tocan posiciones bloqueadas.
- [ ] No muta automáticamente las versiones publicadas; genera una propuesta pendiente de confirmación.
