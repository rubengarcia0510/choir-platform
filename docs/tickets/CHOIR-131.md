# CHOIR-131 — Inicializar un director de prueba en desarrollo local

## Objetivo

Permitir crear una cuenta de director para pruebas manuales en el entorno
local, sin habilitar el registro público ni modificar la autenticación
existente.

La inicialización debe ser explícita, segura e idempotente.

## Contexto técnico verificado

- `src/main/resources/application.yml` define `local` como perfil
  predeterminado.
- El perfil `local` utiliza H2 en memoria.
- `DirectorRepository` expone `findByEmailIgnoreCase` y `save`.
- `Director.create(displayName, email)` construye la entidad.
- `SecurityConfiguration` proporciona un `PasswordEncoder` basado en BCrypt.
- `DirectorUserDetailsService` requiere contraseña almacenada y
  `emailVerified = true` para autenticar.
- El registro público permanece deshabilitado en la configuración actual.

## Diseño requerido

Crear un inicializador independiente dentro del paquete:

`com.rubengarcia.choir.auth`

Nombre sugerido:

`LocalDevelopmentDirectorInitializer`

Responsabilidades:

1. Ejecutarse exclusivamente con el perfil `local`.
2. Requerir una propiedad explícita para habilitarse:
   `choir.auth.local-director.enabled=true`.
3. Leer estas variables de entorno:
   - `CHOIR_DEV_DIRECTOR_NAME`
   - `CHOIR_DEV_DIRECTOR_EMAIL`
   - `CHOIR_DEV_DIRECTOR_PASSWORD`
4. Validar que los valores estén presentes y sean válidos.
5. Normalizar el email con `trim()` y minúsculas usando `Locale.ROOT`.
6. Consultar `DirectorRepository.findByEmailIgnoreCase`.
7. Si la cuenta existe, no modificarla.
8. Si no existe:
   - Crear mediante `Director.create`.
   - Codificar la contraseña con el `PasswordEncoder` inyectado.
   - Establecer `emailVerified` en `true`.
   - Persistir mediante `DirectorRepository.save`.

Usar inyección por constructor.

La inicialización debe integrarse con el ciclo de arranque de Spring y
completar la operación reactiva antes de considerar finalizada la
inicialización. Evitar suscripciones reactivas desacopladas (`subscribe()`)
que oculten errores de persistencia.

## Archivos previstos

- Nuevo:
  `src/main/java/com/rubengarcia/choir/auth/LocalDevelopmentDirectorInitializer.java`
- Configuración:
  `src/main/resources/application.yml`
- Documentación:
  `README.md` o una sección específica de desarrollo local.
- Pruebas:
  `src/test/java/com/rubengarcia/choir/auth/`

Antes de implementar, verificar los paquetes y convenciones reales del
repositorio. No crear clases duplicadas ni introducir otra estrategia de
persistencia.

## Configuración

La propiedad debe estar deshabilitada por defecto:

`choir.auth.local-director.enabled=false`

Sólo debe poder habilitarse en el perfil `local`.

No agregar valores de nombre, email o contraseña por defecto. No guardar
credenciales reales en archivos versionados.

## Seguridad

- No habilitar el registro público.
- No modificar `DirectorRegistrationService`.
- No modificar `AuthController`, DTOs ni contratos HTTP.
- No almacenar contraseñas en texto plano.
- No imprimir contraseñas ni hashes en logs.
- No sobrescribir cuentas existentes.
- No ejecutar el inicializador en el perfil `prod`.
- No agregar usuarios de prueba a migraciones SQL.
- No agregar endpoints de administración ni bypass de autenticación.

## Pruebas requeridas

Crear pruebas que cubran:

1. Cuenta inexistente: crea un director.
2. Contraseña: se almacena el resultado del `PasswordEncoder`, nunca el
   texto original.
3. Verificación: la cuenta creada queda con `emailVerified = true`.
4. Idempotencia: un segundo arranque no duplica ni modifica la cuenta.
5. Cuenta existente: no reemplaza contraseña ni datos.
6. Configuración deshabilitada: no crea cuentas.
7. Variables faltantes: produce un diagnóstico claro sin revelar secretos.
8. Perfil `prod`: el inicializador no se registra ni ejecuta.

Las pruebas deben respetar la arquitectura reactiva y utilizar las
herramientas de testing existentes.

## Documentación de uso

Documentar cómo habilitar el inicializador y exportar las tres variables
de entorno antes de ejecutar `./gradlew bootRun`.

Aclarar que H2 está configurada en memoria y que los datos pueden perderse
al reiniciar el backend.

No documentar ni introducir credenciales fijas.

## Criterios de aceptación

- La cuenta puede iniciar sesión por `/api/auth/login`.
- La contraseña está almacenada con BCrypt.
- El email queda verificado.
- El mecanismo sólo funciona en desarrollo local y con habilitación
  explícita.
- La ejecución repetida no modifica cuentas existentes.
- Producción permanece sin cambios.
- Las pruebas y el build backend finalizan correctamente.

## Entrega

- Rama: `feature/CHOIR-131-local-dev-director`
- PR hacia la rama de integración backend vigente.
- Incluir resultados de pruebas y pasos para configurar la cuenta local.
