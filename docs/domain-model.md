# Choir Platform — modelo de dominio

## Entidades y relaciones

- Director administra uno o varios coros.
- Coro pertenece a un director y contiene coreutas, repertorio, conciertos, formaciones y transiciones.
- Coreuta pertenece a un coro.
- Concierto pertenece a un coro y tiene repertorio ordenado; cada aparición conserva su duración estimada y necesidades escénicas.
- Ítem de repertorio pertenece a un coro; `concert_repertoire` asocia obras a conciertos, conserva su orden y sus datos escénicos específicos.
- Las reglas especiales de escena pertenecen a una obra del repertorio, no a una transición. Tienen tipo, descripción, orden, estado activo y grupos ordenados opcionales, definidos por cantidad (`COUNT`) o por IDs explícitos de coreutas (`IDENTIFIED`).
- La lectura histórica de reglas devuelve las revisiones inmutables con sus grupos, modos de composición, cantidades e IDs identificados capturados. No completa esos snapshots desde las fichas actuales de coreutas.
- La captura de elenco conserva nombre, cuerda vocal e identidad/nombre de sección vocal tal como eran al capturar el elenco. La referencia al coreuta es opcional para no perder la captura si se elimina esa ficha. La identidad y el nombre de sección son datos históricos sin FK a la sección mutable; renombrar o reasignar un coreuta no modifica la captura.
- `concert_vocal_assignments` representa la asignación de coreutas a divisiones vocales por obra y concierto. Al publicar el concierto, `concert_vocal_assignment_snapshots` conserva una copia histórica por obra y coreuta: nombre, cuerda, sección y número de división. La referencia al coreuta puede quedar nula si se elimina la ficha; los datos descriptivos capturados se conservan.
- Formación identifica una formación lógica. Cada versión de formación tiene número propio y estado; sus posiciones pertenecen a esa versión, no a la formación abstracta. Las posiciones conservan nombre, cuerda, identidad/nombre de sección y, cuando se conoce, división vocal. Las capturas no dependen del nombre actual de la sección.
- Transición identifica una transición lógica. Cada versión de transición conserva sus versiones de formación de origen y destino. La consulta `positionsForTransitionVersion` expone las posiciones históricas de ambos lados, identificadas como `FROM` y `TO`, utilizando los snapshots de las versiones de formación; no reconstruye las divisiones desde las asignaciones actuales de los coreutas.
- Las versiones de transición se listan y crean como borradores en `/api/choirs/{choirId}/transitions/{transitionId}/versions`. La creación verifica que las dos versiones exactas de formación pertenezcan al coro y sean distintas. La numeración se serializa por transición dentro del adaptador de persistencia. La publicación es una operación de persistencia para integrar desde el caso de uso de CHOIR-94 después de evaluar las reglas; no se expone un endpoint de publicación directa que pueda omitir esa evaluación. Publicar cambia solo `DRAFT` a `PUBLISHED` y no archiva ni sobrescribe versiones anteriores.
- Ausencia pertenece a un coreuta y puede estar asociada a un concierto.

## Flujo de publicación y captura histórica

El elenco se configura con `PUT /api/choirs/{choirId}/concerts/{concertId}/cast` y se consulta con `GET` sobre la misma ruta. Al guardar una selección se captura inmediatamente nombre, cuerda y sección vocal para que cambios posteriores en las fichas del coro no alteren el concierto. Si no hubo selección explícita, el elenco activo se captura al publicar.

El catálogo de obras del coro se consulta y amplía con `GET` y `POST /api/choirs/{choirId}/repertoire`. El repertorio del concierto se consulta y reemplaza ordenado con `GET` y `PUT /api/choirs/{choirId}/concerts/{concertId}/repertoire`. Cada elemento incluye duración estimada y necesidades escénicas; la lista es reemplazada transaccionalmente y solo admite obras del coro asociado.

Las reglas especiales de una canción se consultan, crean, leen individualmente, editan, reordenan y desactivan en `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/scene-rules`. Cada operación valida que el director sea propietario del coro y que la obra pertenezca al repertorio de ese concierto. Un grupo `COUNT` requiere `memberCount` positivo y no acepta IDs; un grupo `IDENTIFIED` requiere `singerIds` no vacíos, únicos y pertenecientes al coro, y su cantidad se deriva de esos IDs. Si se omite `compositionMode`, la API conserva compatibilidad e interpreta el grupo como `COUNT`. Editar grupos reemplaza su definición ordenada dentro de la misma transacción; desactivar conserva la regla y sus grupos. El orden solicitado incluye todas las reglas, también las desactivadas. Cada creación, edición, reordenamiento y desactivación agrega un snapshot inmutable en `scene_rule_revisions`, `scene_rule_revision_groups` y, para grupos identificados, `scene_rule_revision_group_members`; los IDs históricos no dependen de las fichas actuales de coreutas. Las consultas y transacciones de estas mutaciones pertenecen al adaptador de persistencia. Las migraciones V19 y V20 crean las reglas y revisiones, y V20 captura el estado actual como revisión inicial; V21 añade los modos de composición y los integrantes identificados sin reconstruir cambios anteriores que no se habían registrado. Esta API administra las restricciones de la canción; su integración con el cálculo de transiciones y su historial publicado se desarrolla en CHOIR-67.

La lectura de revisiones para planificación recupera todas las versiones inmutables de una obra, con grupos, modo de composición y miembros identificados tal como fueron capturados; no consulta las fichas actuales de coreutas. El modelo persistido actualmente no contiene las condiciones estructuradas que CHOIR-20/90 requieren para evaluar reglas. `description` es informativa y no se puede interpretar como lógica; por eso las reglas no pueden producir un resultado determinista hasta que se acuerde y persista ese contrato estructurado.

El endpoint `POST /api/choirs/{choirId}/concerts/{concertId}/publish` delega en `ConcertPublicationService`. El servicio comprueba que el concierto pertenezca al coro indicado, que el director autenticado sea su propietario y que el concierto esté en estado `PLANNED` o `REHEARSAL`.

Dentro de una transacción, la publicación cambia el estado a `PUBLISHED`, registra `published_at` y captura:
- El elenco efectivo del concierto en `concert_cast_snapshots` (sin sobrescribir una selección ya capturada).
- Las asignaciones vocales por cada ítem del repertorio en `concert_vocal_assignment_snapshots`, incluyendo el número de división correspondiente.

Si el concierto ya está publicado o en cualquier estado distinto de `PLANNED` y `REHEARSAL`, la operación falla en lugar de volver a publicar y duplicar las capturas. La actualización condicional protege también frente a intentos concurrentes. El cambio de estado y las capturas deben confirmarse o revertirse juntos.

Los snapshots son registros históricos: no deben reconstruirse ni actualizarse desde los datos actuales del coreuta o de la sección. Para datos históricos anteriores a la introducción de una columna, se conserva `NULL` cuando no existe evidencia para determinar el valor; no se inventan divisiones.

## Decisiones de persistencia

1. `V1__initial_schema.sql` se conserva sin cambios. El dominio se agrega mediante migraciones posteriores.
2. Las claves primarias son numéricas generadas por la base de datos.
3. Las restricciones `CHECK`, `UNIQUE` y las claves foráneas protegen estados válidos, numeración de versiones y relaciones básicas.
4. Las capturas del elenco y de las posiciones conservan nombres, cuerdas e identidad/nombre de sección vocal aunque cambien las fichas actuales o se renombre una sección. Las columnas históricas de sección no tienen FK a la sección mutable.
5. La división es opcional en capturas antiguas y generales. La migración no inventa divisiones históricas; las asignaciones por obra se representan en las tablas de asignación vocal y su snapshot.
6. Una versión de formación publicada debe permanecer inmutable; los cambios posteriores se representan con una versión nueva, no sobrescribiendo la publicada. Esta regla debe validarse en los servicios de dominio y sus pruebas.
7. Las reglas que relacionan coro, concierto, coreuta, repertorio, asignación vocal y formación deben validarse en la capa de servicio además de las claves foráneas.
8. La información histórica de sección/división está disponible en las tablas de snapshots y posiciones para que los servicios de planificación puedan consultarla. `positionsForFormationVersion` consulta una versión de formación y `positionsForTransitionVersion` devuelve las posiciones de origen (`FROM`) y destino (`TO`) de una versión de transición. Los cambios posteriores en las fichas de coreutas o secciones no deben alterar esos datos históricos. La integración de las divisiones como restricciones del algoritmo puede desarrollarse en una fase posterior.
9. Los grupos de reglas de escena conservan su modo de composición. Los grupos `IDENTIFIED` almacenan el orden y los IDs de coreutas en tablas propias; los snapshots históricos no tienen FK a las fichas actuales, para que una modificación o eliminación futura no altere lo registrado.

## Migración y reversión

Las migraciones V5 a V11 agregan el modelo vocal sin modificar V1. No se deben inferir asignaciones históricas cuando los datos de origen son ambiguos.

El procedimiento manual está documentado en `docs/migrations/rollback-CHOIR-56.sql`. Es destructivo: requiere un backup verificado y probarse primero sobre una copia de la base de datos. Elimina los datos y estructuras introducidos por estas migraciones y no modifica el historial de Flyway. No es un mecanismo de rollback automático del despliegue.

## Estado del flujo vocal

La captura histórica de asignaciones vocales por obra, la gestión de asignaciones y la validación de planes vocales están implementadas en CHOIR-61, CHOIR-62 y CHOIR-63, respectivamente.

La integración de las divisiones vocales como restricciones del algoritmo de formación queda para una fase posterior. Los cambios de esta fase no deben alterar las asignaciones ni los snapshots históricos.

## Reglas de negocio del plan vocal (CHOIR-57)

1. **Población N:** se cuentan los coreutas activos del coro cuya cuerda habitual corresponde a cada cuerda del plan. Las ausencias de un concierto no alteran N.
2. **Cantidad de divisiones:** k es la cantidad de divisiones persistidas para esa cuerda. Si N < k, el plan requiere revisión; no se borran, fusionan ni renumeran divisiones o asignaciones automáticamente.
3. **Asignación por obra:** el director puede asignar explícitamente un coreuta a una división del plan de una obra, incluso si esa cuerda difiere de su cuerda habitual. La asignación no modifica la cuerda habitual ni las asignaciones de otras obras.
4. **Ajustes por concierto:** las asignaciones de `concert_vocal_assignments` son específicas de una aparición de obra en un concierto y no modifican el plan base ni las asignaciones reutilizables de la obra. No se heredan automáticamente asignaciones de `work_vocal_assignments`: las advertencias y la captura histórica del concierto se calculan sobre las asignaciones explícitas de esa aparición. Las divisiones sin asignaciones generan advertencias.
5. **Captura histórica:** al publicar un concierto en estado `PLANNED` o `REHEARSAL`, dentro de la misma transacción se cambia su estado a `PUBLISHED` y se capturan el elenco activo y las asignaciones vocales explícitas del concierto. Una publicación repetida se rechaza; los snapshots no se reconstruyen desde datos actuales.
6. **Migración asistida:** la correspondencia entre `voice_part` y una cuerda configurable requiere confirmación explícita del director. Solo se migran coreutas sin cuerda configurable asignada; se conserva el valor legado y no se infieren correspondencias ambiguas.
7. **Recuperación:** las migraciones V5–V11 son aditivas y no modifican V1 ni rellenan datos históricos que no existían. El script de rollback elimina estructuras y datos nuevos, por lo que no conserva esos datos. Antes de usarlo hay que verificar un backup y ensayar tanto el rollback como la restauración en una copia de la base. La recuperación de los datos eliminados depende de restaurar el backup; el script no revierte automáticamente el historial de Flyway.
