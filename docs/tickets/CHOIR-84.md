# CHOIR-84 — Implementar bloqueo y desbloqueo de posiciones de coreutas

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-84 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-84).

## Objetivo y alcance

Permitir al director fijar una posición artística para evitar movimientos
accidentales. Persistir el estado bloqueado y evitar que las operaciones
habituales cambien sus coordenadas. El director autorizado puede bloquear y
desbloquear; el backend expone el estado al frontend para mostrarlo claramente.

El bloqueo se limita a las coordenadas de esa formación: no impide quitar al
coreuta de la formación ni incluirlo en otra.

## Criterios de aceptación

- Una posición bloqueada conserva sus coordenadas ante intentos de movimiento.
- El director puede desbloquearla y luego moverla.
- El backend valida el bloqueo; no depende solo del frontend.
- Se respetan las autorizaciones existentes.

## Plan de implementación

1. **Persistencia y dominio:** confirmar la siguiente versión Flyway disponible
   (sin asumir V19), agregar `formation_positions.locked NOT NULL DEFAULT
   FALSE` en las variantes de migración que use el proyecto, e incorporar el
   campo en `FormationPosition`, entidad y mapper. Las posiciones históricas
   deben migrar desbloqueadas.
2. **Puerto y operaciones de aplicación:** definir operaciones cohesivas de
   bloqueo/desbloqueo para una posición de un borrador identificado por coro,
   formación y versión. El servicio conserva la autorización del director,
   valida el contexto completo, solo permite versiones `DRAFT` y hace ambas
   operaciones idempotentes.
3. **Adaptador HTTP:** exponer bloqueo/desbloqueo con el estilo de rutas y
   respuestas existente. La lectura de posiciones incluye `locked`; errores de
   autorización, contexto inválido y versión publicada mantienen los códigos
   HTTP del proyecto.
4. **Protección persistente de movimientos:** actualizar el guardado completo y
   la edición individual en los adaptadores. Preservar el bloqueo al guardar
   posiciones existentes, rechazar coordenadas distintas con conflicto, crear
   nuevas desbloqueadas y permitir omitir/eliminar una posición bloqueada. El
   conflicto del guardado completo debe ocurrir antes de cualquier escritura.
   Coordinar el cambio de coordenadas con el estado `locked` mediante escritura
   condicional/transacción para cerrar carreras entre lock y move.
5. **Ciclo de vida de versiones:** al crear un borrador desde una versión
   publicada de la misma formación, copiar el bloqueo; una duplicación hacia
   otra formación no hereda ese estado y comienza desbloqueada.
6. **Pruebas backend:** cubrir migración default, DTO, idempotencia,
   autorización/aislamiento de coro, formación y versión, restricciones de
   estado, movimiento individual y reemplazo completo, eliminación, altas
   nuevas, herencia/duplicación, rollback y carrera entre bloqueo y movimiento.
   Mantener JDBC limitado a migraciones y pruebas; las escrituras runtime
   siguen en R2DBC.
7. **Frontend (`choir-platform-web`):** en su repositorio y rama de ticket,
   extender el modelo/API/editor, indicador y acción accesible de lock,
   impedir drag, refrescar tras mutaciones y probar persistencia tras guardar y
   recargar. Integrar el cambio solo cuando los contratos backend estén
   definidos y disponibles.
8. **Cierre:** ejecutar pruebas enfocadas y suites H2/PostgreSQL del backend,
   pruebas frontend, revisar `git diff --check`, documentar resultados y
   completar CI antes del PR a `main`.

### Estado de implementación

- Plan derivado del issue GitHub [#82](https://github.com/rubengarcia0510/choir-platform/issues/82);
  Jira no fue consultado.
- Rama backend: `feature/CHOIR-84-lock-singer-positions`, creada desde `main`
  actualizado.
- Completadas en este repositorio: fases 1 a 6. El backend persiste y expone el
  bloqueo, autoriza las mutaciones, impide movimientos protegidos, conserva el
  estado al versionar y lo reinicia al duplicar otra formación.
- Validación local H2: `compileTestJava` y las suites
  `FormationCatalogPersistenceAdapterTests` (21), `FormationVersionLifecycleIntegrationTests` (8),
  `FormationPlanningServiceTests` (9) y `FormationDraftControllerTests` (6)
  pasaron; 44 pruebas en total.
- Pendiente: fase 7 en el repositorio separado `choir-platform-web`; no está
  disponible en este workspace.
- Pendiente para cierre: ejecutar CI, incluida la suite PostgreSQL, y completar
  la integración del frontend antes de considerar terminado el alcance total
  del ticket.
