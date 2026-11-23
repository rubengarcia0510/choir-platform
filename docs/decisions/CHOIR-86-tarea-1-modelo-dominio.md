# CHOIR-86 — Tarea 1: modelo de dominio de restricciones físicas

Complementa `docs/decisions/CHOIR-86-physical-constraints.md`, que define la
semántica. Este documento registra lo implementado en esta tarea y lo que
queda pendiente.

## Decisiones resueltas

- **Movilidad y visibilidad son modelos separados.** `FormationMobilityConstraint`
  y `FormationVisibilityConstraint` no comparten campos ni puertos.
- **Región de movilidad.** `MobilityRegion` tiene cuatro límites, `minX`,
  `maxX`, `minY` y `maxY`. Son obligatorios, se comparan por valor y deben
  cumplir `minX < maxX` y `minY < maxY`. Los límites son inclusivos según la
  semántica documentada; la pertenencia de una posición a la región no se
  implementa en esta tarea.
- **Escala 0..100.** Aprobada: los cuatro límites se validan en [0, 100], inclusive.
  Las coordenadas usan el mismo sistema que `formation_positions.x_coordinate` e
  `y_coordinate` (`DECIMAL(10, 3)`). Ver `CHOIR-86-coordenadas.md`. Esta decisión
  reemplaza la versión original de la tarea 1, que no imponía rango.
- **Sin coordenada Z.** `MobilityRegion` solo tiene los cuatro límites.
- **Altura.** No se modifica `heightCm` ni `heightCmSnapshot`; ya están
  implementados en los commits de CHOIR-86 anteriores a esta tarea.
- **Campos de texto libre.** No se reutilizan `singers.mobility` ni
  `singers.restrictions`.
- **Contratos.** Se definen puertos de entrada y de salida para listar,
  crear, actualizar y eliminar restricciones de movilidad y de visibilidad,
  con tipos explícitos. Los errores de invariantes son
  `IllegalArgumentException`, igual que en el servicio de proximidad.

## Visibilidad: configuración sin evaluación

`FormationVisibilityConstraint` solo representa el coreuta protegido, la
formación y si la restricción está activa. No contiene orientación escénica,
dirección de visión, obstrucciones ni altura, porque su regla no está definida.
No se declara ninguna posición como cumplida ni se usa un valor por defecto.

## Pendientes (bloquean la evaluación, no el modelo)

1. **Orientación escénica:** no está definida qué lado del escenario es el
   frente ni cómo se mide.
2. **Dirección de visión hacia el público:** no está definida.
3. **Regla geométrica de obstrucción:** no está definido qué coreuta obstruye a
   cuál, ni con qué criterio de distancia o ángulo, ni cómo se usa `heightCmSnapshot`.
4. **Tratamiento de altura ausente:** la semántica dice «NO EVALUABLE»; falta
   definir cómo se informa en el resultado de la validación.
5. **Relación entre coreutas protegidos y obstrucciones:** falta el modelo de
   esa relación, si es que debe existir como restricción separada.

## Fuera de esta tarea

- Persistencia: tablas, migraciones (la V38 no se toca) y adaptadores SQL.
- Controladores HTTP y adaptadores de entrada.
- Servicios que implementen los casos de uso de estos puertos.
- Validación geométrica de movimientos.
- Composite de validadores de formación (CHOIR-87).
- Integración con `transition`: no se agregó ninguna dependencia.

## Verificación de esta tarea

- `./gradlew test` **no se pudo ejecutar** en el entorno de trabajo: Gradle no
  puede descargar su distribución. Queda pendiente en CI o en un entorno local.
- Las pruebas de `formation/domain` se compilaron y ejecutaron con JUnit
  simulado y un runner mínimo: 11 pruebas, 11 aprobadas. Es una verificación
  local y no reemplaza la ejecución con JUnit real.
- Los puertos se compilaron contra tipos simulados de Reactor.
