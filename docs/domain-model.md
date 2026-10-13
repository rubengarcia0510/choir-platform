
Choir Platform — modelo de dominio

Entidades y relaciones

- Director administra uno o varios coros.
- Coro pertenece a un director y contiene coreutas, repertorio,
  conciertos, formaciones y transiciones.
- Coreuta pertenece a un coro.
- Concierto pertenece a un coro y tiene repertorio ordenado.
- Captura de elenco conserva nombre y cuerda vocal tal como eran
  al momento de capturar el elenco del concierto. La referencia al
  coreuta es opcional para no perder la captura si se elimina esa ficha.
- Ítem de repertorio pertenece a un coro; "concert_repertoire"
  asocia obras a conciertos y conserva su orden.
- Formación identifica una formación lógica. Cada versión de
  formación tiene número propio y estado; sus posiciones pertenecen
  a esa versión, no a la formación abstracta.
- Transición identifica una transición lógica. Cada versión de
  transición conserva sus versiones de formación de origen y destino.
- Ausencia pertenece a un coreuta y puede estar asociada a un concierto.

Decisiones de persistencia

1. "V1__initial_schema.sql" se conserva sin cambios. El dominio se agrega
   mediante "V2__choir_domain_model.sql".
2. Las claves primarias son numéricas generadas por la base de datos.
3. Las restricciones "CHECK", "UNIQUE" y las claves foráneas protegen
   estados válidos, numeración de versiones y relaciones básicas.
4. Las capturas del elenco y de las posiciones conservan nombres y cuerdas
   vocales aunque cambien las fichas actuales de los coreutas.
5. Una nueva versión publicada debe insertarse como un registro nuevo;
   no se debe sobrescribir una versión ya publicada. La aplicación deberá
   aplicar esta regla en los servicios de dominio y sus pruebas.
6. Las reglas que relacionan coro, concierto, coreuta y formación deben
   validarse en la capa de servicio además de las claves foráneas.

Fuera del alcance de esta migración

Esta migración define la estructura relacional. No implementa todavía
autenticación, endpoints, servicios de publicación ni políticas de
autorización; corresponden a tareas posteriores.
