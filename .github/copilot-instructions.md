# Instrucciones de desarrollo para CHOIR

## Ramas por ticket

- Al comenzar un ticket nuevo, creá una rama nueva dedicada a ese ticket antes de modificar archivos. Partí de `main` actualizado y no reutilices una rama de otro ticket. Mantené todos los cambios del ticket en esa rama; no trabajes directamente en `main`.

## Especificaciones de tickets Jira

- Antes de trabajar en un ticket CHOIR, consultá [docs/tickets/README.md](../docs/tickets/README.md) y leé el archivo `docs/tickets/CHOIR-<número>.md` correspondiente.
- Usá el spec local como contexto inicial; verificá Jira si el ticket no tiene spec o si necesitás confirmar cambios posteriores al snapshot indicado en el índice.
- Si cambia el alcance acordado en Jira, actualizá el spec del ticket en una rama dedicada y mantené explícita la fecha del snapshot.

## Aplicar SOLID durante el desarrollo

Usá SOLID como guía para tomar decisiones y revisar cambios, no como requisito de crear interfaces, capas o abstracciones en cada implementación. Antes de modificar código, inspeccioná los patrones existentes y mantené la solución más simple que preserve responsabilidades claras.

- **Responsabilidad única (SRP):** cada clase o método debe tener un propósito cohesivo. Mantené HTTP y validación de requests en controladores, reglas del dominio y autorización en servicios, y acceso a datos en repositorios. Si una clase empieza a cambiar por motivos distintos, separá solo la responsabilidad que el cambio necesita.
- **Abierto/cerrado (OCP):** preferí extender el comportamiento mediante los puntos de extensión que el proyecto ya usa. No agregues jerarquías, estrategias o configuraciones anticipadas para un único caso; introducí una abstracción cuando exista más de una implementación real o un cambio recurrente que la justifique.
- **Sustitución de Liskov (LSP):** cualquier implementación de una interfaz debe respetar sus contratos, incluidos resultados, errores, autorización y comportamiento reactivo. No ocultes fallos ni devuelvas valores por defecto que aparenten éxito.
- **Segregación de interfaces (ISP):** definí contratos pequeños y cohesivos, relacionados con una capacidad concreta. Evitá interfaces generales que obliguen a sus implementaciones a depender de operaciones que no utilizan.
- **Inversión de dependencias (DIP):** los servicios coordinan casos de uso y dependen de contratos adecuados en los límites del dominio. Seguí la inyección de dependencias de Spring y los patrones existentes; no agregues interfaces para clases que no necesitan implementaciones alternativas ni abstraigas detalles sin una frontera útil.

## Respetar la arquitectura hexagonal

El proyecto debe mantener una separación hexagonal entre el núcleo de la aplicación y sus adaptadores. Tratá los controladores HTTP como adaptadores de entrada, los servicios como coordinación de casos de uso y los repositorios como adaptadores de salida. Mantené las reglas del dominio fuera de los detalles de transporte y persistencia, y evitá que el núcleo dependa de frameworks o de tecnologías de infraestructura cuando los límites existentes lo permitan. En particular, las consultas R2DBC y los límites transaccionales que coordinan varias escrituras persistentes pertenecen a los adaptadores de salida/persistencia, no a los servicios de aplicación.

Usá puertos o interfaces en los límites cuando aporten desacoplamiento útil y seguí los contratos que el proyecto ya tenga. No agregues capas, puertos ni interfaces ceremoniales, ni hagas una refactorización arquitectónica amplia como parte de un cambio funcional acotado. Antes de editar, inspeccioná la estructura existente y resolvé cada cambio dentro de sus límites sin romper los patrones actuales.

## Proceso para cada cambio

1. Trazá el caso de uso y las reglas existentes antes de editar. Revisá controladores, servicios, repositorios, migraciones y pruebas relacionadas para no duplicar lógica ni saltarte autorización.
2. Asigná cada regla a una responsabilidad apropiada. Validá las entradas en el límite HTTP y reforzá en el servicio las invariantes del dominio que también deben cumplirse para otros llamadores.
3. Conservá las propiedades reactivas con `Mono`/`Flux`; no bloquees flujos de runtime. Usá R2DBC para persistencia de runtime. JDBC queda limitado a Flyway y a pruebas o utilidades que lo requieran explícitamente.
4. Preferí contratos y cambios pequeños que resuelvan la necesidad actual. No introduzcas capas, interfaces, estados ni opciones especulativas para anticipar requisitos no confirmados.
5. Agregá o ajustá pruebas para el comportamiento esperado, los errores relevantes, la autorización y las reglas de estado. Mantené los tests alineados con la semántica real del caso de uso.
6. Actualizá la documentación del dominio cuando cambien reglas o transiciones persistentes. Para cambios de esquema, usá una migración Flyway nueva; no reescribas migraciones ya aplicadas.
7. Ejecutá las pruebas más específicas que cubran el cambio y las validaciones de formato disponibles. El proyecto requiere JDK 21; informá con claridad si el entorno no permite ejecutar una validación.

## Convenciones del proyecto

- Usá Java 21 y seguí el estilo de los componentes existentes de Spring Boot.
- Conservá autorización explícita del director sobre el coro y los recursos asociados.
- Hacé explícitos los errores de entrada, estado y persistencia; no uses catches amplios, defaults silenciosos ni retornos que simulen éxito.
- Protegé las transiciones concurrentes con actualizaciones condicionales o restricciones apropiadas, y mantené dentro de una transacción los cambios que deban confirmarse juntos.
- No agregues dependencias ni cambies límites entre JDBC y R2DBC sin una necesidad concreta.

Consultá [README.md](../README.md) para los requisitos, comandos y límites de persistencia, y [docs/domain-model.md](../docs/domain-model.md) para las reglas del dominio y las decisiones de persistencia.
