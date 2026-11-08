# CHOIR-26 — Permitir preferencias artísticas del director en la optimización

- **Estado:** ⏳ Pendiente (Fase 3 — Cálculo y Optimización)
- **Fuente de verdad:** [CHOIR-26 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-26)
- **Dependencia técnica previa:** CHOIR-25 (Ranking de alternativas)
- **Desbloquea:** Ajuste fino de motor de optimización
- **Frontend consumidor:** `choir-platform-web` (CHOIR-26-FRONTEND — Sliders/pesos de optimización)

---

## 1. Objetivo y Alcance

Permitir al director configurar **pesos y prioridades artísticas** para ponderar las funciones de coste del optimizador sin convertirlo en una caja negra:
* Prioridad de **menor distancia**: minimizar el esfuerzo físico y pasos.
* Prioridad de **evitar cruces**: asegurar que ningún coreuta se cruce por delante de otro.
* Prioridad de **continuidad de cuerda vocal**: intentar que las secciones permanezcan juntas durante el desplazamiento.

**Decisiones de alcance pendientes de confirmar en Jira antes de implementar:**
- El texto funcional menciona simultaneidad, pero el payload propuesto no incluye un peso de simultaneidad.
- El criterio de aceptación permite persistir por concierto o formación sin decidir cuál es el alcance.

No añadir campos al contrato ni elegir un ámbito de persistencia hasta confirmar estas decisiones. Los criterios de arriba describen candidatos y no amplían por sí solos el contrato.

---

## 2. Contrato HTTP

* Las rutas y el alcance de preferencias quedan sujetos al contrato confirmado del backend; la ruta siguiente es una propuesta no implementable hasta validarla:
  * `GET /api/choirs/{choirId}/concerts/{concertId}/optimization-preferences`
  * `PUT /api/choirs/{choirId}/concerts/{concertId}/optimization-preferences`
  * Payload:
```json
{
  "weightDistance": 0.4,
  "weightCrossings": 0.4,
  "weightVocalSectionCohesion": 0.2
}
```

---

## 3. Criterios de Aceptación

- [ ] El alcance de persistencia (concierto o formación) está definido y probado.
- [ ] La suma de pesos se valida y normaliza en el backend.
- [ ] La respuesta de las alternativas generadas (CHOIR-25) explica qué factores determinaron el ranking.
- [ ] El conjunto de pesos expuestos coincide exactamente con los criterios confirmados y el contrato HTTP.
