# Especificaciones de tickets CHOIR — Backend

Este directorio contiene los snapshots y especificaciones de implementación de los tickets Jira del backend (`choir-platform`).

> 📌 Para consultar el orden de ejecución sugerido, el grafo de dependencias técnicas y la relación con el frontend, consulta el documento principal: **[ROADMAP.md](ROADMAP.md)**.

---

## Estado Actual de los Tickets en `main`

| Ticket | Estado | Título |
|---|---|---|
| **[CHOIR-84](CHOIR-84.md)** | ✅ Completado | Implementar bloqueo y desbloqueo de posiciones de coreutas |
| **[CHOIR-109](CHOIR-109.md)** | ✅ Completado | Generar posiciones iniciales agrupadas por cuerda vocal |
| **[CHOIR-68](CHOIR-68.md)** | ✅ Completado | Verificar reglas especiales y documentación de CHOIR-20 |
| **[CHOIR-99](CHOIR-99.md)** | ✅ Completado | Hacer fiable la limpieza de datos de pruebas de evaluación en PostgreSQL |
| **[CHOIR-105](CHOIR-105.md)** | ✅ Completado | Verificar migraciones y compatibilidad de datos existentes |
| **[CHOIR-106](CHOIR-106.md)** | ✅ Completado | Ejecutar matriz integral de aceptación de versionado |
| **[CHOIR-89](CHOIR-89.md)** | ✅ Completado | Crear formación predeterminada agrupada por cuerda vocal (cerrada con CHOIR-109 y CHOIR-110) |
| **[CHOIR-85](CHOIR-85.md)** | ⏳ Pendiente (Fase 1) | Definir y validar restricciones opcionales de proximidad entre coreutas |
| **[CHOIR-86](CHOIR-86.md)** | ⏳ Pendiente (Fase 1) | Modelar restricciones configurables de movilidad y visibilidad |
| **[CHOIR-87](CHOIR-87.md)** | ⏳ Pendiente (Fase 1) | Validar movimientos contra bloqueos y restricciones de formación |
| **[CHOIR-88](CHOIR-88.md)** | ⏳ Pendiente (Fase 1) | Probar persistencia, permisos y validación de restricciones |
| **[CHOIR-20](CHOIR-20.md)** | ⏳ Pendiente (Fase 2) | Configurar reglas especiales de escena por canción |
| **[CHOIR-67](CHOIR-67.md)** | ⏳ Pendiente (Fase 2) | Integrar reglas especiales en la planificación de transiciones |
| **[CHOIR-24](CHOIR-24.md)** | ⏳ Pendiente (Fase 3) | Modelar y puntuar transiciones entre formaciones |
| **[CHOIR-23](CHOIR-23.md)** | ⏳ Pendiente (Fase 3) | Bloquear decisiones artísticas y definir restricciones en optimizador |
| **[CHOIR-25](CHOIR-25.md)** | ⏳ Pendiente (Fase 3) | Generar y rankear alternativas de transición |
| **[CHOIR-26](CHOIR-26.md)** | ⏳ Pendiente (Fase 3) | Permitir preferencias artísticas del director en la optimización |
| **[CHOIR-28](CHOIR-28.md)** | ⏳ Pendiente (Fase 4) | Recalcular formaciones y transiciones ante ausencias |
| **[CHOIR-29](CHOIR-29.md)** | ⏳ Pendiente (Fase 4) | Comparar y aplicar adaptación por ausencia |
| **[CHOIR-30](CHOIR-30.md)** | ⏳ Pendiente (Fase 5) | Modo ensayo y reproducción de transiciones |
| **[CHOIR-32](CHOIR-32.md)** | ⏳ Pendiente (Fase 5) | Vista de coreuta con sus posiciones y movimientos |
| **[CHOIR-82](CHOIR-82.md)** | ℹ️ Documentación | Incorporar CI para choir-platform-web |

---

## Cómo usar las especificaciones

1. Consulta **[ROADMAP.md](ROADMAP.md)** para conocer el orden de ejecución y las dependencias previas necesarias antes de iniciar un ticket.
2. Identifica la clave Jira del ticket a trabajar y lee su archivo correspondiente.
3. Respeta la arquitectura hexagonal existente (`adapter.in.web`, `application.usecase`, `application.port`, `adapter.out.persistence`).
4. Revisa dependencias y límites para no duplicar trabajo entre backend y frontend.
5. Al finalizar cada ticket, incorpora sus pruebas unitarias y de integración en PostgreSQL / H2 antes de abrir el Pull Request correspondiente.
