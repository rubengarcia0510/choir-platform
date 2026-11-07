# CHOIR-109 — Backend: generar posiciones iniciales agrupadas por cuerda vocal

- Estado al snapshot: Tareas por hacer.
- Fuente de verdad: https://rubengarcia0510.atlassian.net/browse/CHOIR-109
- Historia padre: https://rubengarcia0510.atlassian.net/browse/CHOIR-89
- Snapshot de instrucciones: 2026-11-06.

## Objetivo

Reemplazar la distribución inicial genérica de posiciones por una distribución espacial determinista que mantenga agrupados a los coreutas de cada sección vocal configurada para el coro. La agrupación solo define las coordenadas iniciales: las posiciones continúan siendo completamente editables.

## Contexto técnico confirmado en main

La creación del borrador inicial ya existe y se ejecuta desde:

- `FormationCatalogPersistenceAdapter.createWithInitialDraft(...)`
- `FormationCatalogPersistenceAdapter.createInitialDraft(...)`
- `FormationCatalogPersistenceAdapter.findInitialAssignments(...)`
- `FormationCatalogPersistenceAdapter.createPositions(...)`

`findInitialAssignments` consulta las asignaciones activas del concierto/obra y las ordena por `vs.sort_order`, división, nombre e ID del coreuta. También obtiene los snapshots de coreuta, voz, sección y división.

Actualmente `createPositions` asigna coordenadas mediante una grilla genérica de nueve columnas (`GRID_COLUMNS`) y los métodos `xCoordinate` / `yCoordinate`. Ese orden de inserción no garantiza que las secciones queden espacialmente agrupadas.

La persistencia de las posiciones ya ocurre dentro de la transacción de creación del borrador. No reemplazar ese flujo ni introducir un endpoint o caso de uso nuevo para este ticket.

## Instrucciones de implementación

1. Antes de editar, revisar el flujo completo de creación del borrador, los modelos `InitialPosition` y `FormationPosition`, las restricciones de coordenadas de la base de datos y los casos de uso actuales de edición/reemplazo de posiciones.
2. Modificar la generación de coordenadas en `FormationCatalogPersistenceAdapter.createPositions` (o extraer un colaborador únicamente si mejora claramente la cohesión). Mantener la consulta de asignaciones y el flujo transaccional existentes salvo que la inspección revele una necesidad concreta.
3. Agrupar por identidad de sección vocal, no por el texto del nombre ni por `voice_part`. Usar `voiceSectionId` como clave: los nombres son configurables y no deben determinar la identidad.
4. Respetar el orden de secciones definido por `sort_order`, que ya se aplica en la consulta. Dentro de cada sección, preservar el orden estable existente por división, nombre e ID de coreuta.
5. Asignar a cada sección un bloque espacial contiguo y no solapado. Todos los coreutas de una sección deben quedar juntos en ese bloque; una sección numerosa puede ocupar más de una fila dentro de su propio bloque. Las secciones siguientes deben ocupar bloques distintos, sin intercalar integrantes de secciones diferentes.
6. Calcular coordenadas deterministas y normalizadas dentro del espacio válido de la formación. Respetar los límites y la precisión establecidos por el esquema y los modelos actuales. La solución debe funcionar con cualquier cantidad de secciones y cantidades desiguales de integrantes: no fijar cuatro secciones, nombres, tamaños ni una capacidad arbitraria por sección.
7. Mantener todos los snapshots que hoy se persisten: ID de coreuta, nombre, voz, ID/nombre de sección y número de división. No cambiar su semántica.
8. Si no hay asignaciones elegibles, conservar el comportamiento actual: crear la formación y su borrador sin posiciones. No inventar posiciones ni fallar por una lista vacía.
9. No tocar formaciones, versiones ni posiciones que no pertenezcan al borrador recién creado. No modificar el caso de uso de edición/reemplazo ni imponer reglas espaciales permanentes.
10. Mantener Java 21, Reactor y la arquitectura hexagonal. SQL y detalles R2DBC permanecen en el adaptador de persistencia; no mover consultas a servicios de aplicación ni introducir JDBC en runtime.

## Archivos y pruebas a revisar

- Implementación: `src/main/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapter.java`.
- Integración de creación inicial: `src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogInitialDraftIntegrationTests.java`.
- Pruebas del adaptador: `src/test/java/com/rubengarcia/choir/formation/adapter/out/persistence/FormationCatalogPersistenceAdapterTests.java`.

Ampliar las pruebas de integración para comprobar las coordenadas realmente persistidas, no solamente el orden de los objetos en memoria.

### Casos mínimos

- Una sección: todas las posiciones quedan dentro de un único bloque y conservan el orden interno.
- Dos secciones: dos bloques separados, sin intercalado.
- Cuatro secciones y más de cuatro: todos los bloques permanecen diferenciados y siguen `sort_order`.
- Nombres no convencionales y nombres similares: la agrupación depende del ID de sección, no de etiquetas conocidas.
- Cantidades desiguales, incluyendo una sección mucho más numerosa, y varias divisiones: no hay solapamientos y se conserva el orden interno por división/nombre/ID.
- IDs de secciones cuyo orden numérico no coincide con `sort_order`: prevalece `sort_order`.
- Sin asignaciones elegibles: se crea el borrador sin posiciones.
- Snapshots: se mantienen los datos de sección, división, voz y coreuta.
- Aislamiento: crear una formación nueva no cambia las coordenadas ni los registros de otras formaciones/versiones del mismo coro.
- Edición posterior: las posiciones iniciales siguen pudiendo reemplazarse mediante el caso de uso existente; la agrupación no se convierte en una restricción.

Para validar el orden espacial, las pruebas deben consultar las coordenadas persistidas y asociarlas con los IDs/snapshots de sección. No alcanza con verificar que la lista de asignaciones esté ordenada antes de generar posiciones.

## Criterios de aceptación

- El borrador inicial persiste posiciones agrupadas espacialmente por sección vocal.
- La agrupación respeta `sort_order` y el orden interno de cada sección.
- La solución no depende de cantidad ni nombres fijos de secciones y soporta grupos de tamaños distintos.
- Los snapshots existentes permanecen intactos y las coordenadas son válidas, deterministas y no se solapan.
- No se alteran otras formaciones/versiones y la edición posterior continúa funcionando.
- Las pruebas automatizadas cubren los casos mínimos anteriores y pasan junto con las validaciones pertinentes del proyecto.

## Fuera de alcance

- Cambios en Angular o en `choir-platform-web`.
- Nuevo endpoint, nuevo flujo de creación o duplicación de formaciones.
- Reglas de proximidad, movilidad, bloqueo o restricciones espaciales.
- Refactorizaciones generales del adaptador o de la arquitectura.
- Cambios de esquema/migraciones, salvo que la inspección demuestre que son imprescindibles. En ese caso, detenerse y justificarlo antes de implementarlos.

## Validación y entrega

- Ejecutar primero las pruebas específicas de creación inicial y del adaptador; luego la suite completa si el entorno lo permite.
- Informar los comandos ejecutados y sus resultados reales. No afirmar que una prueba pasó si no se ejecutó.
- Mantener el cambio limitado a CHOIR-109 y entregar una rama/PR apuntando a `main`.
- No incluir cambios del frontend CHOIR-110.
