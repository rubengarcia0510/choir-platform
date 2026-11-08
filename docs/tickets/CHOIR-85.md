# CHOIR-85 — Definir y validar restricciones opcionales de proximidad entre coreutas

- **Estado:** ⏳ Pendiente (Fase 1 — Motor de Restricciones)
- **Fuente de verdad:** [CHOIR-85 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-85)
- **Dependencia técnica previa:** CHOIR-84 (Bloqueo de posiciones — Completado en PR #83)
- **Desbloquea:** CHOIR-87 (Validación centralizada de movimientos) y CHOIR-88 (Suite de pruebas)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-85-FRONTEND)

---

## 1. Objetivo y Alcance

Permitir al director configurar relaciones espaciales opcionales de **proximidad** entre coreutas específicos dentro del contexto de una formación, sin imponer un agrupamiento automático por cuerda/sección.

Definir un modelo explícito en la arquitectura hexagonal, una tabla de persistencia en PostgreSQL / H2, y endpoints REST para crear, consultar y eliminar restricciones de proximidad entre pares de coreutas `(singerA, singerB)` con una distancia euclidiana máxima admisible `maxDistance`.

---

## 2. Contrato de Entrada y Modelo de Dominio

### Modelo de Dominio (`formation.domain` o `formation.constraint`)
* `ProximityConstraint`:
  * `id`: identificador `Long` de la restricción, consistente con las entidades del backend.
  * `formationId`: ID de la formación a la que pertenece.
  * `firstSingerId`: ID del primer coreuta.
  * `secondSingerId`: ID del segundo coreuta (distinto de `firstSingerId`).
  * `maxDistance`: valor numérico positivo expresado en las mismas unidades que las coordenadas `x`/`y` del escenario. Las coordenadas actuales son porcentajes normalizados entre `0` y `100`; no interpretar esta distancia como metros. La fórmula es $\sqrt{(x_1 - x_2)^2 + (y_1 - y_2)^2} \le \text{maxDistance}$.
  * Invariantes: ambos coreutas deben pertenecer al elenco de la formación. No pueden ser la misma persona. No puede haber restricciones de proximidad duplicadas o inversas para el mismo par en la misma formación.

### Contrato HTTP
* Base: `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations/{formationId}/constraints/proximity`
* `POST /`: Crea una restricción de proximidad.
  * Payload: `{ "firstSingerId": 12, "secondSingerId": 15, "maxDistance": 2.5 }`
  * Respuesta: `201 Created` con metadatos de la restricción.
* `GET /`: Lista las restricciones de proximidad activas para la formación.
* `DELETE /{constraintId}`: Elimina una restricción existente (`204 No Content`).

---

## 3. Instrucciones de Implementación

1. **Persistencia (Flyway):**
   * Crear una migración Flyway nueva usando la siguiente versión libre del repositorio, con claves foráneas hacia `formations` y `singers`. No usar literalmente `VXX`.
   * Incluir índice único para evitar pares repetidos en la misma formación.
2. **Puertos y Adaptadores:**
   * Crear puerto de salida `FormationConstraintPort` y su adaptador R2DBC en `formation.adapter.out.persistence`.
   * Asegurar atomicidad y aislamiento multitenant: validar que `choirId`, `concertId` y `formationId` coincidan con el director autenticado.
3. **Casos de Uso:**
   * `ConfigureProximityConstraintUseCase`: valida pertenencia de los coreutas al elenco de la formación antes de persistir.
   * `DeleteProximityConstraintUseCase`: elimina la restricción verificando autorización.
4. **Validación espacial determinista:**
   * Implementar `ProximityConditionValidator`: método puro que recibe las posiciones actuales del borrador y determina si la condición se cumple o produce una violación con mensaje explicativo: `El coreuta A y el coreuta B deben estar a una distancia máxima de X, pero se encuentran a Y`.
   * Definir precisión y redondeo del mensaje sin redondear los valores usados para decidir si la regla se cumple.

---

## 4. Criterios de Aceptación

- [ ] Se pueden crear, consultar y eliminar restricciones de proximidad mediante API REST.
- [ ] No permite crear restricciones con el mismo coreuta en ambos extremos.
- [ ] No permite registrar pares duplicados (A con B y B con A) en la misma formación.
- [ ] Rechaza solicitudes de directores no autorizados (`403 Forbidden`).
- [ ] Compara `maxDistance` con coordenadas del mismo sistema y unidad; no asume metros ni escala física no configurada.
- [ ] La precisión mostrada al director no altera la comparación numérica.
- [ ] No presupone cercanía obligatoria por sección vocal.
