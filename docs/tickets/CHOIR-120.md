# CHOIR-120 — Evaluar secuencias temporales de reglas de movimiento

- Estado: Documentación inicial.
- Tipo: Backend.
- Padre: CHOIR-20.
- Prioridad: Medium.

## Objetivo

Completar la evaluación de reglas especiales de tipo `MOVEMENT` que requieren orden temporal (`OrderingMode.SEQUENTIAL`).

La evaluación debe utilizar información temporal explícita y verificable. No debe inferir el orden de los movimientos a partir de posiciones estáticas ni del estado actual de los coreutas.

## Contexto

Actualmente, las reglas `CAST_DIVISION` con `OrderingMode.SEQUENTIAL` pueden evaluarse utilizando restricciones espaciales.

En cambio, las reglas `MOVEMENT` con `OrderingMode.SEQUENTIAL` pueden resultar `NOT_EVALUABLE` porque los snapshots utilizados por la evaluación no contienen información temporal suficiente.

El contrato actual de publicación bloquea las evaluaciones `NOT_EVALUABLE`. Este comportamiento debe mantenerse cuando falten datos temporales.

## Alcance

1. Revisar el contrato actual de snapshots y los inputs utilizados por el evaluador de reglas.
2. Definir una representación explícita de la información temporal necesaria para evaluar movimientos secuenciales.
3. Implementar el traslado y la preservación de esa información a través del flujo de evaluación.
4. Evaluar correctamente las reglas `MOVEMENT` con `OrderingMode.SEQUENTIAL` cuando exista información temporal suficiente.
5. Mantener el resultado `NOT_EVALUABLE` cuando los datos temporales sean inexistentes o incompletos.
6. Preservar la relación entre evaluación, snapshots y versiones concretas.
7. Mantener la inmutabilidad de la evidencia de publicaciones históricas.
8. Actualizar la documentación del contrato de evaluación y snapshots.

## Criterios de aceptación

- [ ] Una regla `MOVEMENT` secuencial con información temporal suficiente puede evaluarse como `SATISFIED` o `VIOLATED`, según corresponda.
- [ ] La evaluación distingue correctamente una secuencia válida de una secuencia incumplida.
- [ ] La ausencia de información temporal produce `NOT_EVALUABLE`.
- [ ] La información temporal incompleta produce `NOT_EVALUABLE` con una advertencia accionable.
- [ ] No se infiere el orden temporal a partir de posiciones estáticas ni del estado actual de los coreutas.
- [ ] La evaluación utiliza los snapshots y versiones concretos que corresponden.
- [ ] Las publicaciones históricas conservan su evidencia original y no cambian ante modificaciones posteriores.
- [ ] Existen pruebas unitarias para secuencias válidas, incumplidas, ausentes e incompletas.
- [ ] Existen pruebas de integración para el flujo de evaluación y publicación.
- [ ] La implementación se valida en H2 y PostgreSQL.
- [ ] La documentación refleja el contrato temporal implementado.

## Fuera de alcance

- Modificar el CRUD o la autorización de reglas especiales (CHOIR-65 y CHOIR-66).
- Modificar el versionado de reglas o su historial append-only (CHOIR-67).
- Cambiar la evaluación espacial de `CAST_DIVISION` con `OrderingMode.SEQUENTIAL`.
- Implementar ranking o restricciones artísticas de CHOIR-24 y CHOIR-25.
- Inferir secuencias temporales a partir de posiciones o estados actuales.
- Modificar el contrato general de publicación para permitir publicar evaluaciones `NOT_EVALUABLE`.

## Consideraciones de arquitectura

- Mantener la arquitectura hexagonal por dominio.
- La lógica de evaluación debe permanecer en el dominio correspondiente.
- El acceso a datos y las consultas SQL deben permanecer en los adapters.
- Evitar acoplar el evaluador a detalles de persistencia.
- Preservar la trazabilidad entre snapshots, evaluaciones y evidencia publicada.

## Validaciones requeridas

| Escenario | Resultado esperado |
|---|---|
| Secuencia temporal completa y válida | SATISFIED |
| Secuencia temporal completa pero incumplida | VIOLATED |
| Información temporal ausente | NOT_EVALUABLE |
| Información temporal incompleta | NOT_EVALUABLE |
| Modificación posterior de datos de origen | La evidencia histórica no cambia |

## Dependencias

- CHOIR-65: Modelar y persistir reglas especiales de escena por canción.
- CHOIR-66: Implementar CRUD y autorización de reglas especiales.
- CHOIR-67: Integrar reglas especiales en la planificación de transiciones.
- CHOIR-68: Verificar reglas especiales y documentación de CHOIR-20.

## Definición de terminado

La tarea se considera terminada cuando todos los criterios de aceptación están cubiertos, las pruebas pasan en H2 y PostgreSQL, la documentación está actualizada y los cambios están integrados mediante PR.
