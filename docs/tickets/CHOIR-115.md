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
- Si un concierto no tiene configuración explícita, se utilizan valores predeterminados deterministas.
- Cambiar la formación de un concierto no debe modificar ni trasladar sus preferencias.

## 3. Criterios artísticos

La configuración contempla estos cuatro criterios:

1. **Distancia:** minimizar el desplazamiento de los coreutas.
2. **Cruces:** reducir o evitar cruces de trayectorias.
3. **Compactación:** favorecer una distribución escénica compacta.
4. **Continuidad de secciones:** favorecer la cohesión de las secciones vocales durante la transición.

El contrato debe representar estos criterios de forma estructurada.

Los rangos, normalización, valores predeterminados y representación HTTP definitiva deben quedar definidos antes de implementar la persistencia. No asumir una suma de pesos ni agregar criterios adicionales sin decisión explícita.

## 4. Responsabilidades backend

- Consultar las preferencias de un concierto.
- Crear o actualizar las preferencias de un concierto.
- Persistirlas con una relación inequívoca con el concierto.
- Validar que el concierto pertenezca al coro indicado.
- Validar que el director autenticado esté autorizado sobre ese coro.
- No confiar en un identificador de director enviado por el cliente.
- Aplicar valores predeterminados deterministas cuando no exista configuración.
- Exponer las preferencias al flujo de optimización mediante contratos de aplicación.
- Mantener la lógica de dominio independiente de Spring y de la infraestructura.

## 5. Integración con CHOIR-25

CHOIR-115 no debe implementar un segundo algoritmo de ranking.

La generación y el ranking de alternativas pertenecen a CHOIR-25. La integración de preferencias debe consumir ese mecanismo y ajustar la evaluación según la configuración del concierto.

La separación de responsabilidades es:

- CHOIR-24: métricas y evaluación de transiciones.
- CHOIR-25: generación y ranking de alternativas.
- CHOIR-115: persistencia y provisión de preferencias por concierto.
- CHOIR-116: interfaz para editar preferencias y explicar el ranking.

La persistencia y consulta de preferencias pueden avanzar independientemente de CHOIR-25. La integración efectiva con el ranking requiere el contrato de alternativas de CHOIR-25.

No introducir dependencias circulares entre estos tickets.

## 6. Explicabilidad y reproducibilidad

La evaluación debe permitir justificar por qué una alternativa quedó por encima de otra.

La información expuesta debe permitir relacionar:

- Las métricas de cada alternativa.
- Las preferencias configuradas para el concierto.
- La contribución de cada criterio al resultado.
- El orden final obtenido.

Las mismas alternativas, métricas y preferencias deben producir el mismo resultado.

## 7. Criterios de aceptación

- [ ] Las preferencias se consultan y actualizan con autorización.
- [ ] Cada concierto mantiene una configuración independiente.
- [ ] No se heredan preferencias desde la formación ni desde otros conciertos.
- [ ] La ausencia de configuración utiliza valores predeterminados deterministas.
- [ ] Los cuatro criterios están representados en el contrato acordado.
- [ ] Se validan los datos recibidos y sus rangos según el contrato definitivo.
- [ ] Se valida la pertenencia del concierto al coro y la autorización del director.
- [ ] La integración utiliza el ranking de CHOIR-25 sin duplicarlo.
- [ ] La respuesta permite explicar la prioridad de las alternativas.
- [ ] Existen pruebas de persistencia, permisos, validación, valores predeterminados y reproducibilidad.
- [ ] Se verifica la persistencia con H2 y PostgreSQL cuando corresponda.

## 8. Fuera de alcance

- Componentes Angular y decisiones visuales.
- Generación de alternativas de transición.
- Reemplazo del algoritmo de CHOIR-24 o CHOIR-25.
- Modificación de formaciones originales.
- Incorporación de criterios artísticos no acordados.

## 9. Decisiones pendientes antes de implementar

- Definir los rangos y la normalización de los cuatro criterios.
- Definir los valores predeterminados del optimizador.
- Cerrar el contrato HTTP y su representación de errores.
- Confirmar cómo CHOIR-25 recibirá las preferencias y expondrá la explicación del ranking.
