# CHOIR-29 — Comparar y aplicar adaptación por ausencia

- **Estado:** ⏳ Pendiente (Fase 4 — Ausencias y Adaptación)
- **Fuente de verdad:** [CHOIR-29 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-29)
- **Dependencia técnica previa:** CHOIR-28 (Cálculo de adaptación ante ausencias)
- **Desbloquea:** Publicación de versiones adaptadas ante contingencias
- **Frontend consumidor:** `choir-platform-web` (CHOIR-29-FRONTEND — Diff visual antes/después)

---

## 1. Objetivo y Alcance

Permitir al director **comparar la propuesta generada en CHOIR-28 frente al estado original** y decidir explícitamente si aplicar la propuesta, ajustarla manualmente en el editor o descartarla.

Si el director decide aplicar la adaptación, el backend crea un **nuevo borrador o versión** de cada formación involucrada respetando el flujo de versionado inmutable (CHOIR-106).

---

## 2. Contrato HTTP

* `POST /api/choirs/{choirId}/concerts/{concertId}/adaptations/absence/apply`
* Payload:
```json
{
  "adaptationProposalId": "prop-882",
  "approvedFormationIds": [55, 56]
}
```
* Respuesta: `200 OK` con las nuevas versiones/borradores creados.

---

## 3. Criterios de Aceptación

- [ ] Devuelve los datos necesarios para renderizar un diff visual (posiciones originales vs nuevas).
- [ ] La aplicación de la propuesta es atómica.
- [ ] No sobrescribe versiones históricas ni borra auditoría previa.
