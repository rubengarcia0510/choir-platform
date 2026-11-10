# Especificaciones de tickets CHOIR — Backend

Este directorio contiene las especificaciones de implementación de los
tickets Jira del backend (`choir-platform`).

Para consultar el orden de ejecución y las dependencias, revisar
[ROADMAP.md](ROADMAP.md).

## Estado de tickets documentados en main

| Ticket | Estado | Título |
|---|---|---|
| [CHOIR-84](CHOIR-84.md) | Completado | Bloqueo y desbloqueo de posiciones |
| [CHOIR-109](CHOIR-109.md) | Completado | Posiciones iniciales agrupadas por cuerda |
| [CHOIR-68](CHOIR-68.md) | Completado | Verificación de reglas especiales |
| [CHOIR-99](CHOIR-99.md) | Completado | Limpieza de datos de pruebas PostgreSQL |
| [CHOIR-105](CHOIR-105.md) | Completado | Migraciones y compatibilidad |
| [CHOIR-106](CHOIR-106.md) | Completado | Matriz de aceptación de versionado |
| [CHOIR-89](CHOIR-89.md) | Completado | Formación predeterminada por cuerda |
| [CHOIR-20](CHOIR-20.md) | Completado | Reglas especiales de escena por canción |
| [CHOIR-67](CHOIR-67.md) | Completado | Integración de reglas en planificación |
| [CHOIR-120](CHOIR-120.md) | Completado | Evaluación de secuencias temporales |
| [CHOIR-85](CHOIR-85.md) | Pendiente | Restricciones de proximidad |
| [CHOIR-86](CHOIR-86.md) | Pendiente | Restricciones de movilidad y visibilidad |
| [CHOIR-87](CHOIR-87.md) | Pendiente | Validación de movimientos |
| [CHOIR-88](CHOIR-88.md) | Pendiente | Pruebas de restricciones y permisos |
| [CHOIR-24](CHOIR-24.md) | Pendiente — dividido en subtareas | Modelar y puntuar transiciones |
| [CHOIR-121](https://rubengarcia0510.atlassian.net/browse/CHOIR-121) | Pendiente | Métricas de desplazamiento |
| [CHOIR-122](https://rubengarcia0510.atlassian.net/browse/CHOIR-122) | Pendiente | Cruces y conflictos de trayectorias |
| [CHOIR-123](https://rubengarcia0510.atlassian.net/browse/CHOIR-123) | Pendiente | Integración y resultado reproducible |
| [CHOIR-23](CHOIR-23.md) | Pendiente | Decisiones artísticas y restricciones |
| [CHOIR-25](CHOIR-25.md) | Pendiente | Generación y ranking de alternativas |
| [CHOIR-26](CHOIR-26.md) | Pendiente | Preferencias artísticas |
| [CHOIR-28](CHOIR-28.md) | Pendiente | Recálculo ante ausencias |
| [CHOIR-29](CHOIR-29.md) | Pendiente | Comparación y aplicación de adaptación |
| [CHOIR-30](CHOIR-30.md) | Pendiente | Modo ensayo |
| [CHOIR-32](CHOIR-32.md) | Pendiente | Vista individual del coreuta |
| [CHOIR-82](CHOIR-82.md) | Documentación | CI del frontend |

## Cómo usar las especificaciones

1. Consultar el roadmap antes de iniciar un ticket.
2. Tomar Jira como fuente de verdad para alcance y criterios.
3. Respetar la arquitectura hexagonal:
   `adapter.in.web`, `application.usecase`, `application.port`,
   `adapter.out.persistence`.
4. No duplicar responsabilidades de otros tickets.
5. Incorporar pruebas unitarias y de integración.
6. Validar persistencia en PostgreSQL y H2 cuando corresponda.
