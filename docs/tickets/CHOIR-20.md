# CHOIR-20 — Configurar reglas especiales de escena por canción

- **Estado:** ⏳ Pendiente (Fase 2 — Reglas Especiales de Escena)
- **Fuente de verdad:** [CHOIR-20 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-20)
- **Dependencia técnica previa:** CHOIR-68 (Regresiones y documentación de reglas — Completado en PR #81)
- **Desbloquea:** CHOIR-67 (Integración de reglas en planificación de transiciones)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-20-FRONTEND — Panel de reglas de repertorio)

---

## 1. Objetivo y Alcance

Permitir al director expresar y configurar, para cada canción del repertorio de un concierto, **reglas artísticas especiales** que no pueden modelarse como una transición genérica o que requieren una coreografía específica:
* Movimientos o salidas por grupos identificados o cantidades fijas (ej. salida 4+4).
* Movimientos escalonados o en tandas sucesivas.
* Agrupaciones temporales específicas para solistas o momentos de la obra.

Las reglas pertenecen a la canción (`repertoireItemId`) dentro del concierto y se evalúan durante la planificación de sus transiciones; no forman parte de una formación aislada ni alteran snapshots históricos.

---

## 2. Contrato HTTP y Modelo de Dominio

### Endpoints
* Base: `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/scene-rules`
* `GET /`: Lista las reglas de escena configuradas para la canción, ordenadas por su secuencia de ejecución.
* `POST /`: Crea una regla de escena nueva.
* `PUT /{ruleId}`: Modifica la configuración de una regla (condiciones, orden, grupos).
* `DELETE /{ruleId}`: Desactiva la regla para cálculos futuros. Debe conservar la revisión/historial que ya forme parte de evaluaciones o conciertos; no borrar evidencia histórica. La implementación puede usar borrado lógico, no borrado físico.

---

## 3. Criterios de Aceptación

- [ ] Las reglas soportan ordenamiento secuencial explícito.
- [ ] Permite definir grupos específicos por IDs de coreutas o por cantidades/cuerdas.
- [ ] Soporta reglas de simultaneidad y reglas secuenciales (por tandas).
- [ ] Toda modificación genera una versión/revisión inmutable para reproducir cálculos posteriores.
- [ ] La eliminación excluye la regla de cálculos futuros sin borrar evidencia histórica.
