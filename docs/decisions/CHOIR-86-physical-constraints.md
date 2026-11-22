# CHOIR-86 — Decisiones de restricciones físicas

## 1. Movilidad

La restricción de movilidad utiliza una región rectangular en el
plano de coordenadas de la formación.

Propiedades:
- minX
- maxX
- minY
- maxY

Una posición cumple cuando:

- minX <= x <= maxX
- minY <= y <= maxY

Los límites son inclusivos.

Se rechazan regiones con minX >= maxX o minY >= maxY.

La región utiliza el mismo sistema de coordenadas que
formation_positions.x_coordinate e y_coordinate.

No se agrega una coordenada Z.

Una posición fuera de la región produce un hallazgo de movilidad
(`MobilityFinding`). El código `MOBILITY_VIOLATION` de la especificación del ticket
queda para la validación de CHOIR-87; la evaluación implementada no lo emite.

## 2. Altura y visibilidad

La altura es un atributo propio del coreuta, no de la formación.

Se incorpora como:
- heightCm: entero expresado en centímetros.
- Opcional: puede no estar informado.

El dato pertenece al perfil del coreuta y se administra mediante
los endpoints existentes de creación y actualización de singers.

## 3. Snapshot histórico

Cada posición de una versión de formación conserva la altura
utilizada al construir esa posición:

- heightCmSnapshot

La altura se captura desde el perfil del coreuta cuando se guardan
las posiciones del borrador.

Al crear un borrador desde una versión publicada o duplicar una
formación, se conserva el snapshot de origen.

Una modificación posterior de heightCm en el perfil no modifica
versiones históricas.

Si falta la altura, la evaluación de visibilidad debe informarse
como NOT_EVALUABLE (nombre del estado en la implementación). No se debe asumir que la restricción se cumple
ni inventar una altura.

## 4. Validación de visibilidad

La altura se utiliza junto con:
- Las posiciones X/Y de los coreutas.
- La orientación del escenario.
- La dirección de visión hacia el público.
- La relación entre coreutas protegidos y posibles obstrucciones.

La regla geométrica exacta y la orientación escénica todavía
requieren una definición explícita.

No implementar heurísticas ni declarar una validación geométrica
como determinista hasta cerrar esos datos.

No agregar coordenada Z para representar la altura física.

## 5. Puntos de implementación existentes

Perfil:
- Singer
- SingerController.SingerRequest
- SingerService.SingerData
- SingerServiceImpl
- SingerControllerTests

Snapshot:
- FormationPosition
- FormationPositionEntity
- FormationPersistenceMapper
- FormationCatalogPersistenceAdapter.findSingerSnapshot
- FormationCatalogPersistenceAdapter.toFormationPosition
- FormationVersionLifecyclePersistenceAdapter.copyPublishedPositions
- FormationDuplicationPersistenceAdapter.copyPosition

Persistencia:
- Nueva migración Flyway V38.
- singers.height_cm
- formation_positions.height_cm_snapshot

## 6. Pruebas necesarias

- Crear y actualizar un coreuta con altura.
- Consultar un coreuta con altura.
- Permitir altura ausente.
- Persistir y recuperar heightCmSnapshot.
- Verificar que modificar la altura del perfil no altere snapshots existentes.
- Verificar que crear un borrador desde una versión publicada conserve
  el snapshot.
- Verificar que duplicar una formación conserve el snapshot.
- Verificar que la falta de altura produzca un resultado no evaluable
  para visibilidad, no un cumplimiento implícito.

## 6. Estado de implementación

- **Movilidad:** modelo, persistencia (`V39`, `V40`), API REST, evaluación
  geométrica de solo lectura sobre el borrador. Implementado.
- **Visibilidad:** no implementada. Falta definir la relación protegido/obstructor,
  la orientación del escenario y la regla de obstrucción.
- **Validadores y código `MOBILITY_VIOLATION`:** pendientes de CHOIR-87.
- **Escala de coordenadas:** pendiente de confirmación. Ver
  `CHOIR-86-coordenadas.md`.
