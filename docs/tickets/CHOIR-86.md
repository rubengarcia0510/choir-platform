# CHOIR-86 — Modelar restricciones configurables de movilidad y visibilidad

- **Estado:** ⏳ Pendiente (Fase 1 — Motor de Restricciones)
- **Fuente de verdad:** [CHOIR-86 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-86)
- **Dependencia técnica previa:** CHOIR-84 (Bloqueos — Completado en PR #83)
- **Desbloquea:** CHOIR-87 (Validación centralizada) y CHOIR-88 (Suite de pruebas)
- **Frontend consumidor:** `choir-platform-web` (CHOIR-86-FRONTEND)

---

## 1. Objetivo y Alcance

Modelar y persistir restricciones físicas y espaciales del elenco dentro de una formación:
1. **Restricción de Movilidad:** Asignar a un coreuta específico un requerimiento de ubicación accesible (por ejemplo, primera fila, sin escalón o coordenadas de fácil acceso).
2. **Restricción de Visibilidad:** Garantizar que un coreuta con baja estatura o requerimiento visual no quede ubicado inmediatamente detrás de un coreuta más alto en la misma columna/eje escénico.

Evitar aceptar texto libre o reglas vagas que el backend no pueda validar de forma matemática y determinista.

---

## 2. Contrato de Entrada y Modelo de Dominio

### Modelo de Dominio
* `MobilityConstraint`:
  * `singerId`: Coreuta con restricción.
  * Una condición espacial determinista basada en las coordenadas normalizadas del escenario (`x`/`y` entre `0` y `100`); antes de implementar hay que acordar qué región representa una fila accesible y cómo se configura.
* `VisibilityConstraint`:
  * Identidad del coreuta cuya visibilidad se protege y, si aplica, del coreuta que puede obstruirla.
  * La regla requiere una dirección de mirada/escenario y datos de altura u obstrucción con fuente definida. No inferir altura ni línea de visión con datos no disponibles.

### Contrato HTTP
* Base: `/api/choirs/{choirId}/concerts/{concertId}/repertoire/{repertoireItemId}/formations/{formationId}/constraints/physical`
* `POST /mobility`: Configura una restricción de movilidad para un coreuta.
* `POST /visibility`: Configura una regla de visibilidad relativa entre dos integrantes.
* `GET /`: Lista las restricciones físicas activas de la formación.
* `DELETE /{constraintId}`: Elimina una restricción.

El frontend debe esperar a que este contrato esté implementado y mergeado antes de integrar la UI. La matriz del roadmap debe referenciar esta base (`physical`), no una ruta alternativa `/constraints/mobility`.

---

## 3. Instrucciones de Implementación

1. **Esquema de Base de Datos:**
   * Crear una migración Flyway nueva usando la siguiente versión libre del repositorio, con discriminador de tipo (`MOBILITY`, `VISIBILITY`) o tablas normalizadas. No usar literalmente `VXX`.
2. **Lógica de Validación Espacial:**
   * `MobilityValidator`: Verifica las coordenadas `(x, y)` contra una región accesible explícita y configurable; definir la semántica y orientación de esa región antes de codificar.
   * `VisibilityValidator`: Verifica una regla geométrica explícita y reproducible usando metadatos de altura/visibilidad disponibles. Si esos metadatos o la geometría no están definidos, resolver el alcance antes de implementar; no sustituirlos por heurísticas arbitrarias.
3. **Manejo de Errores y Mensajes Estructurados:**
   * Al violar una restricción, el validador genera un código y mensaje comprensible para el director:
     * `MOBILITY_VIOLATION`: "El coreuta Juan Pérez tiene movilidad reducida y debe permanecer en la fila 1".
     * `VISIBILITY_VIOLATION`: "El coreuta María López queda obstruida por Martín Gómez en la línea visual frontal".

---

## 4. Criterios de Aceptación

- [ ] Cada tipo de restricción tiene semántica y validación determinista documentada.
- [ ] No permite valores arbitrarios o textos libres que el validador no pueda verificar numéricamente.
- [ ] Se definen la escala, orientación del escenario y los datos de dominio necesarios para validar movilidad y visibilidad.
- [ ] Endpoints de creación, consulta y eliminación funcionando bajo autorización del director.
- [ ] Las validaciones se ejecutan en memoria antes de persistir cualquier cambio de posiciones.
