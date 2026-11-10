# Límites entre dominios del backend CHOIR

## Propósito

Evitar acoplamiento entre dominios y conflictos de edición durante el desarrollo paralelo.

## Responsabilidades

| Dominio | Responsabilidad |
|---|---|
| formation | Modelo, persistencia y validación de posiciones y restricciones de formación |
| transition | Métricas, geometría de trayectorias y evidencia temporal entre snapshots |
| scene | Reglas especiales de escena y su evaluación |

## Formation

Es responsable de:
- Bloqueos de posiciones.
- Restricciones de proximidad.
- Restricciones de movilidad y visibilidad.
- Validación de movimientos manuales antes de persistir.
- Autorización y persistencia de las restricciones.

Los validadores y su composición pertenecen a este dominio.

## Transition

Es responsable de:
- Comparar versiones exactas de formaciones.
- Calcular distancias individuales y agregadas.
- Detectar intersecciones geométricas.
- Detectar conflictos temporales únicamente cuando exista evidencia explícita.
- Conservar referencias a snapshots y resultados históricos.

No debe importar ni reutilizar validadores de formation. Una distancia calculada no implica por sí misma una violación de una restricción configurada.

## Scene

Es responsable de las reglas especiales de escena asociadas al repertorio y de su evaluación.

Transition puede integrar sus resultados mediante contratos de aplicación existentes, sin duplicar el motor de reglas.

## Contratos entre dominios

Cuando un caso de uso necesite información o comportamiento de otro dominio:

1. Definir el caso de uso consumidor y los datos necesarios.
2. Definir un contrato pequeño en el límite de aplicación correspondiente.
3. Revisar la dirección de dependencia según arquitectura hexagonal.
4. Implementar el adapter de integración sin acceder a detalles internos del proveedor.
5. Probar el contrato y los errores de integración.

No crear interfaces anticipadas para necesidades hipotéticas. No utilizar tablas, SQL ni modelos de persistencia como contratos.

## Patrones

- Strategy: validadores independientes con implementaciones reales.
- Composite: ejecución y recopilación de resultados de varios validadores.
- Evitar Chain of Responsibility cuando se necesite informar el conjunto completo de violaciones.
- Aplicar SOLID sin introducir abstracciones ceremoniales.

## Coordinación

| Tickets | Dominio |
|---|---|
| CHOIR-85, CHOIR-86, CHOIR-87, CHOIR-88 | formation |
| CHOIR-121, CHOIR-122, CHOIR-123 | transition |
| CHOIR-20, CHOIR-67 | scene |

CHOIR-123 integra métricas de transición con reglas de escena. La evaluación de restricciones de formación no forma parte del alcance actual de CHOIR-24.

Si un ticket requiere modificar clases de otro dominio, acordar primero el contrato o un ticket de integración.
