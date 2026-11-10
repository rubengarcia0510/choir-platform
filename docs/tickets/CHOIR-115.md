# CHOIR-115 — Persistir preferencias artísticas por concierto

- **Padre:** [CHOIR-26 — Preferencias artísticas del director](https://rubengarcia0510.atlassian.net/browse/CHOIR-26)
- **Subtarea:** [CHOIR-115 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-115)
- **Responsable:** Rubén
- **Tipo:** Backend

---

## 1. Objetivo

Persistir las preferencias artísticas del director para cada concierto y permitir que el optimizador las utilice al ordenar alternativas de transición.

Las preferencias deben ser explícitas, reproducibles y explicables. No deben modificar las formaciones originales ni reemplazar el algoritmo de generación y ranking.

## 2. Decisión de dominio: ámbito por concierto

Las preferencias pertenecen al **concierto**, no a la formación.

La formación puede variar de un concierto a otro. Por eso, asociar las preferencias a una formación podría trasladar criterios artísticos a un contexto diferente.

Reglas:

- Cada concierto mantiene su propia configuración.
- Las preferencias no se comparten ni heredan entre conciertos.
- Si un concierto no tiene configuración explícita, se utilizan los valores predeterminados.
- Cambiar la formación de un concierto no debe modificar ni trasladar sus preferencias.

## 3. Criterios artísticos y pesos

La configuración contempla cuatro criterios:

| Criterio | Campo | Valor predeterminado |
| --- | --- | --- |
| Distancia | distance | 25 |
| Cruces | crossings | 25 |
| Compactación | compactness | 25 |
| Continuidad de secciones | sectionContinuity | 25 |

Reglas:

- Cada peso es un entero entre 0 y 100, inclusive.
- La suma de los cuatro pesos debe ser exactamente 100.
- Un peso igual a 0 desactiva la influencia de ese criterio.
- La ausencia de configuración equivale a los cuatro valores predeterminados de 25.
- Los valores predeterminados no se persisten implícitamente al consultar las preferencias.

La comparación de contribuciones requiere métricas normalizadas a una escala comparable. Su definición debe respetar las capacidades existentes y el contrato de ranking de CHOIR-25. No implementar una fórmula de ranking paralela.

## 4. Contrato HTTP acordado

### Consultar preferencias

`GET /api/choirs/{choirId}/concerts/{concertId}/optimization-preferences`

- Devuelve la configuración persistida del concierto.
- Si no existe configuración, devuelve correctamente los valores predeterminados:
  - `distance=25`
  - `crossings=25`
  - `compactness=25`
  - `sectionContinuity=25`
- La consulta no crea ni persiste una configuración implícita.

### Crear o reemplazar preferencias

`PUT /api/choirs/{choirId}/concerts/{concertId}/optimization-preferences`

- Crea la configuración si no existe o reemplaza la existente.
- Recibe los cuatro pesos.
- No recibe identidad del director.
- Rechaza pesos no enteros, fuera de rango o cuya suma no sea exactamente 100.

### Autorización

Ambas operaciones requieren autorización sobre el coro y el concierto indicados.

Se debe reutilizar el mecanismo existente de autorización de recursos. La identidad del director se obtiene del contexto autenticado, nunca del request.

## 5. Responsabilidades backend

- Consultar las preferencias de un concierto.
- Crear o reemplazar las preferencias de un concierto.
- Persistirlas con una relación inequívoca con el concierto.
- Validar que el concierto pertenezca al coro indicado.
- Validar que el director autenticado esté autorizado sobre ese coro.
- Validar tipos enteros, rangos individuales y suma total.
- Aplicar valores predeterminados deterministas cuando no exista configuración.
- Exponer las preferencias al flujo de optimización mediante contratos de aplicación.
- Mantener la lógica de dominio independiente de Spring y de la infraestructura.

## 6. Integración con CHOIR-25

CHOIR-115 no debe implementar un segundo algoritmo de ranking.

La generación y el ranking de alternativas pertenecen a CHOIR-25. La integración de preferencias debe consumir ese mecanismo y ajustar la evaluación según la configuración del concierto.

La separación de responsabilidades es:

- CHOIR-24: métricas y evaluación de transiciones.
- CHOIR-25: generación y ranking de alternativas.
- CHOIR-115: persistencia y provisión de preferencias por concierto.
- CHOIR-116: interfaz para editar preferencias y explicar el ranking.

La persistencia y consulta de preferencias pueden avanzar independientemente de CHOIR-25. La integración efectiva con el ranking requiere el contrato de alternativas de CHOIR-25.

No introducir dependencias circulares entre estos tickets.

## 7. Explicabilidad y reproducibilidad

La evaluación debe permitir justificar por qué una alternativa quedó por encima de otra.

La información expuesta debe permitir relacionar:

- Las métricas de cada alternativa.
- Las preferencias configuradas para el concierto.
- La contribución de cada criterio al resultado.
- El orden final obtenido.

Las mismas alternativas, métricas y preferencias deben producir el mismo resultado.

## 8. Criterios de aceptación

- [ ] Las preferencias se consultan y actualizan con autorización.
- [ ] Cada concierto mantiene una configuración independiente.
- [ ] No se heredan preferencias desde la formación ni desde otros conciertos.
- [ ] El GET sin configuración devuelve los cuatro valores predeterminados sin persistirlos.
- [ ] El PUT crea o reemplaza la configuración.
- [ ] Cada peso es entero entre 0 y 100 y la suma es exactamente 100.
- [ ] Un peso 0 excluye ese criterio de la ponderación.
- [ ] Se valida la pertenencia del concierto al coro y la autorización del director.
- [ ] La integración utiliza el ranking de CHOIR-25 sin duplicarlo.
- [ ] La respuesta permite explicar la prioridad de las alternativas cuando esté disponible el contrato de CHOIR-25.
- [ ] Existen pruebas de persistencia, permisos, validación, valores predeterminados y reproducibilidad.
- [ ] Se verifica la persistencia con H2 y PostgreSQL.

## 9. Fuera de alcance

- Componentes Angular y decisiones visuales.
- Generación de alternativas de transición.
- Reemplazo del algoritmo de CHOIR-24 o CHOIR-25.
- Modificación de formaciones originales.
- Incorporación de criterios artísticos no acordados.

## 10. Decisiones pendientes

- Definir la normalización de las métricas de los cuatro criterios, respetando el contrato de CHOIR-25.
- Confirmar cómo CHOIR-25 recibirá las preferencias y expondrá la explicación del ranking.
