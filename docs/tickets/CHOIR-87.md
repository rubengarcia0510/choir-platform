# CHOIR-87 — Validar movimientos contra bloqueos y restricciones de formación

- **Estado:** ⏳ Pendiente (Fase 1 — Motor de Restricciones)
- **Fuente de verdad:** [CHOIR-87 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-87)
- **Dependencias técnicas previas:** CHOIR-84 (Bloqueos), CHOIR-85 (Proximidad), CHOIR-86 (Movilidad/Visibilidad)
- **Desbloquea:** CHOIR-88 (Suite de pruebas) y CHOIR-23 (Decisiones bloqueadas en optimizador)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-87-FRONTEND — Manejo de errores 409/400 en escenario)

---

## 1. Objetivo y Alcance

Centralizar en el backend la **validación integral del guardado de posiciones de un borrador de formación** frente a:
1. Posiciones bloqueadas (`locked = true`).
2. Restricciones de proximidad activas (CHOIR-85).
3. Restricciones de movilidad y visibilidad activas (CHOIR-86).

Validar cada guardado de posiciones antes de persistir en base de datos. Si una posición bloqueada fue movida o si una restricción se incumple, la operación debe ser rechazada de forma atómica. Usar `400 Bad Request` para solicitudes malformadas y `409 Conflict` para conflictos con el estado o restricciones del dominio. La respuesta de conflicto debe incluir violaciones estructuradas con identificadores de coreutas y explicaciones legibles para que el frontend las resalte en el escenario.

---

## 2. Contrato de Error Estructurado

Cuando el endpoint `PUT .../formations/{formationId}/versions/{versionId}/positions` recibe una distribución inválida, responde:

* **HTTP Status:** `409 Conflict` (o `400 Bad Request` si es error de formato).
* **Payload de Error:**
```json
{
  "status": 409,
  "error": "ConstraintViolationException",
  "message": "La formación contiene 2 restricciones espaciales no satisfechas",
  "violations": [
    {
      "type": "LOCKED_POSITION_MOVED",
      "singerId": 14,
      "positionId": 105,
      "message": "La posición de Ana Gómez está bloqueada y no puede ser modificada"
    },
    {
      "type": "PROXIMITY_VIOLATION",
      "firstSingerId": 12,
      "secondSingerId": 15,
      "message": "Pedro y Laura deben estar a una distancia máxima de 2.0 unidades de escenario (distancia actual: 3.4)"
    }
  ]
}
```

---

## 3. Instrucciones de Implementación

1. **Pipeline de Validación en Caso de Uso:**
   * Modificar `SaveDraftPositionsUseCase` (o el caso de uso correspondiente a guardado de borradores):
     1. Obtener posiciones actuales persistidas en el borrador.
     2. Verificar que ninguna posición con `locked == true` que siga presente en el payload haya cambiado sus coordenadas `(x, y)` respecto al estado persistido.
     3. Permitir omitir una posición bloqueada del payload para quitar al coreuta de la formación, conforme al comportamiento existente de CHOIR-108. No exigir desbloquearla para quitarla; solo el cambio de coordenadas de una posición bloqueada presente es una violación.
     4. Invocar al composite de validadores `FormationConstraintValidator`:
        - `ProximityValidator` (CHOIR-85)
        - `MobilityValidator` (CHOIR-86)
        - `VisibilityValidator` (CHOIR-86)
     5. Si hay violaciones, abortar la transacción inmediatamente y lanzar `FormationConstraintViolationException`.
     6. Si no hay violaciones, proceder al guardado y actualización de versión de borrador.
2. **Tratamiento en Controlador Web:**
   * Agregar handler en `@RestControllerAdvice` o en el controlador web para serializar `FormationConstraintViolationException` con la lista de violaciones.
3. **Aislamiento Arquitectónico:**
   * Las reglas de validación deben ser clases de dominio o aplicación puras, sin dependencias de infraestructura web ni R2DBC.

---

## 4. Criterios de Aceptación

- [ ] Los movimientos válidos se guardan normalmente y actualizan la versión del borrador.
- [ ] Mover una posición bloqueada es rechazado con error `LOCKED_POSITION_MOVED`.
- [ ] Omitir una posición bloqueada para quitar al coreuta de la formación sigue permitido; no equivale a mover la posición.
- [ ] Una formación que viole restricciones de proximidad o movilidad es rechazada sin cambios parciales en la base de datos.
- [ ] La respuesta HTTP incluye la lista detallada de violaciones con IDs de coreutas y descripciones legibles.
- [ ] La validación no depende del frontend: el backend rechaza solicitudes inválidas enviadas por cualquier cliente HTTP.
