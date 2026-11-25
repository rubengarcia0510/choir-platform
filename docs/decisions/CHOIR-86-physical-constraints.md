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

Se rechazan regiones con minX >= maxX o minY >= maxY, y regiones con algún límite
fuera de [0, 100].

La región utiliza el mismo sistema de coordenadas que
formation_positions.x_coordinate e y_coordinate: porcentajes normalizados entre 0 y 100,
inclusive. X crece de izquierda a derecha desde la perspectiva del director. Y crece desde
el fondo (y = 0, borde superior de la UI) hacia el frente (y = 100, borde inferior). El
origen (0, 0) es la esquina superior izquierda.

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

La evaluación de visibilidad utiliza exclusivamente heightCmSnapshot. Si falta
la altura requerida para una comparación, el resultado es `NOT_EVALUABLE`; no se
asume que la restricción se cumple ni se inventa una altura.

## 4. Evaluación de visibilidad

La evaluación de visibilidad está implementada como una aproximación geométrica
determinista sobre las posiciones del DRAFT y sus snapshots de altura.

Solo se evalúan restricciones activas. Para cada posición del coreuta protegido,
se comparan las posiciones de los demás coreutas asignados a la ocurrencia. Se
produce un hallazgo por cada combinación protegida/obstructor que cumpla todas
estas condiciones:

- La diferencia horizontal es como máximo 2 puntos:
  `abs(X_obstructor - X_protegido) <= 2`.
- El obstructor está delante, hacia el público:
  `Y_obstructor > Y_protegido`.
- El snapshot de altura del obstructor es estrictamente mayor:
  `heightCmSnapshot_obstructor > heightCmSnapshot_protegido`.

Se evalúan todas las posiciones de cada coreuta; no se elige una posición
arbitrariamente ni se declara ambigüedad por tener varias posiciones.

Los datos requeridos ausentes producen `NOT_EVALUABLE` con un motivo explícito.
Los hallazgos válidos se conservan aunque otras comparaciones no puedan evaluarse.
El roster incluye los coreutas activos asignados a la obra en el concierto,
aunque no tengan posición en el DRAFT; esa ausencia impide declarar completa
la evaluación.

Esta regla es una aproximación basada en X, Y y altura. No simula la línea visual
real, la geometría corporal, el ángulo de visión ni la posición de los ojos.
La tolerancia horizontal y la comparación estricta de alturas son decisiones
funcionales, no una simulación física del escenario.

No se agrega coordenada Z para representar la altura física.

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

Evaluación de visibilidad:
- `VisibilityEvaluator`: evaluación pura de dominio.
- `FormationVisibilityEvaluationService`: caso de uso que autoriza la ocurrencia,
  obtiene restricciones y posiciones DRAFT, carga el roster activo y ejecuta el evaluador.
- `FormationVisibilityRosterPort` y su adapter: lectura específica de asignaciones
  activas desde `concert_vocal_assignments`; el SQL permanece en infraestructura.
- La evaluación no modifica `vocalAssignmentsForPlanning`.

## 6. Pruebas y verificación

Las pruebas cubren:
- Crear, actualizar y consultar un coreuta con altura, y permitir altura ausente.
- Persistir y recuperar `heightCmSnapshot`.
- Conservar snapshots al crear borradores desde versiones publicadas y al duplicar
  formaciones.
- No alterar snapshots históricos cuando cambia la altura del perfil.
- Regla geométrica, tolerancia horizontal, orientación hacia el público y comparación
  estricta de alturas.
- Múltiples posiciones, roster asignado, datos ausentes y resultados `NOT_EVALUABLE`.

El CI #657 terminó en SUCCESS para el commit
`8f517e239a87dfb158016830750639128bfbdaa0` de
`feature/CHOIR-86-physical-constraints`.

## 7. Estado de implementación

- **Altura y snapshots:** implementados.
- **Movilidad:** modelo, persistencia (`V39`, `V40`), API REST y evaluación
  geométrica de solo lectura sobre el borrador. Implementado.
- **Visibilidad:** evaluador puro y servicio de aplicación implementados. La evaluación
  usa el roster activo de la ocurrencia y snapshots de altura del DRAFT.
- **API HTTP de visibilidad:** no implementada.
- **Integración con el composite de validación:** pendiente de CHOIR-87.
- **Integración visual frontend:** pendiente; no declarar la funcionalidad disponible
  en pantallas.
- **Validadores y código `MOBILITY_VIOLATION`:** pendientes de CHOIR-87.
- **Escala de coordenadas:** aprobada, 0 a 100 inclusive, con CHECK en base (V41). Ver
  `CHOIR-86-coordenadas.md`.
