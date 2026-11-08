# CHOIR-68 — Verificar reglas especiales y documentación de CHOIR-20

- Estado: Finalizada.
- Validación local: H2.
- Validación CI: H2 y PostgreSQL completadas correctamente.

## Validación

Las suites enfocadas pasaron localmente con H2: 81 pruebas, 1 omitida por requerir triggers específicos de PostgreSQL, 0 fallos y 0 errores.

La ejecución de CI de la rama finalizó correctamente en H2 y PostgreSQL, según el resultado registrado al cerrar CHOIR-68 en Jira.

La omisión local corresponde a la prueba que requiere los triggers específicos de PostgreSQL; la validación de ese entorno quedó cubierta por CI.
