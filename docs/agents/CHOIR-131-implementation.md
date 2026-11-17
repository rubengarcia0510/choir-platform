# CHOIR-131 — Implementación: director local de desarrollo

## Objetivo

Permitir inicializar una cuenta de director de prueba exclusivamente en el
entorno local, sin habilitar el registro público ni introducir credenciales
fijas en el repositorio.

## Archivos y componentes

### 1. Crear
`src/main/java/com/rubengarcia/choir/auth/LocalDevelopmentDirectorInitializer.java`

Implementar un `ApplicationRunner` con estas condiciones:

- `@Profile("local")`.
- `@ConditionalOnProperty`:
  - prefix: `choir.auth.local-director`
  - name: `enabled`
  - havingValue: `true`
  - matchIfMissing: `false`
- Inyectar por constructor:
  - `DirectorRepository`
  - `PasswordEncoder`
  - `Environment`

Leer exclusivamente estas variables de entorno:

- `CHOIR_DEV_DIRECTOR_NAME`
- `CHOIR_DEV_DIRECTOR_EMAIL`
- `CHOIR_DEV_DIRECTOR_PASSWORD`

Validaciones:
- No inicializar si falta algún valor o está vacío.
- No imprimir ni registrar la contraseña.
- Normalizar el email con `trim()` y `toLowerCase(Locale.ROOT)`.
- No persistir contraseñas en texto plano.

Flujo:
1. Buscar mediante `DirectorRepository.findByEmailIgnoreCase(email)`.
2. Si ya existe, no modificarlo ni reemplazar sus credenciales.
3. Si no existe:
   - Crear con `Director.create(name, email)`.
   - Generar el hash usando el `PasswordEncoder` inyectado.
   - Asignar el hash mediante `setPasswordHash`.
   - Marcar `emailVerified=true`.
   - Persistir con `DirectorRepository.save`.

La operación reactiva debe completarse durante el inicio de la aplicación.
Usar `.block()` únicamente en este límite de inicialización, de forma
controlada. No usar `subscribe()` desacoplado.

### 2. Configuración

Modificar `src/main/resources/application.yml` y agregar:

`choir.auth.local-director.enabled: false`

La inicialización debe estar deshabilitada por defecto.

No habilitarla en el perfil de producción ni cambiar la configuración
existente de registro público.

### 3. Pruebas

Crear pruebas para `LocalDevelopmentDirectorInitializer` que verifiquen:

- Crea el director cuando no existe.
- Persiste un hash BCrypt, nunca la contraseña original.
- Marca el email como verificado.
- Normaliza el email.
- Es idempotente: una segunda ejecución no modifica la cuenta existente.
- No inicializa cuando faltan variables requeridas.
- No expone contraseñas en mensajes o logs.
- Completa la operación reactiva antes de finalizar la inicialización.

Agregar pruebas de contexto cuando las convenciones existentes lo permitan:

- Perfil `local` con propiedad deshabilitada: no crea el inicializador.
- Perfil `local` con propiedad habilitada: registra el inicializador.
- Perfil `prod`, incluso con la propiedad habilitada: no registra el inicializador.

Usar las convenciones y dependencias de testing que ya existen en el proyecto.
No introducir infraestructura de pruebas innecesaria.

### 4. Documentación

Documentar en `docs/`:

- Que la función es exclusivamente local.
- Cómo habilitarla con `CHOIR_DEV_DIRECTOR_NAME`,
  `CHOIR_DEV_DIRECTOR_EMAIL` y `CHOIR_DEV_DIRECTOR_PASSWORD`.
- Cómo ejecutar la aplicación local con esas variables.
- Que la base H2 en memoria pierde los datos al reiniciar.
- Que nunca debe habilitarse en producción.

No documentar ni agregar credenciales reales.

## Restricciones obligatorias

No modificar:

- `DirectorRegistrationService`.
- `AuthController`.
- DTOs ni contratos HTTP.
- Endpoints existentes.
- Migraciones.
- Reglas generales de autenticación o autorización.
- Configuración de producción.

No agregar:
- Endpoints administrativos.
- Bypass de autenticación.
- Usuarios o contraseñas hardcodeados.
- Valores por defecto para las credenciales.
- Dependencias innecesarias.

Reutilizar `Director`, `DirectorRepository` y el `PasswordEncoder`
existentes. No duplicar la lógica de BCrypt.

## Verificación

Ejecutar:

1. Las pruebas nuevas.
2. Las pruebas existentes del módulo de autenticación.
3. `./gradlew test`.
4. `./gradlew build`.

Resolver cualquier regresión introducida por los cambios.

## Entrega

- Trabajar únicamente en `feature/CHOIR-131-local-dev-director`.
- No modificar ni cerrar otros tickets.
- No hacer merge.
- Preparar un resumen de archivos modificados, decisiones técnicas,
  pruebas ejecutadas y resultados.
- Dejar la rama lista para revisión mediante PR hacia la rama integradora
  backend vigente, verificando primero cuál es la rama destino correcta.
