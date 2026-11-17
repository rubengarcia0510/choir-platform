# CHOIR Platform

Backend de CHOIR para organizar coreutas, conciertos y formaciones.

## Requisitos

- JDK 21.
- Git.
- Para desarrollo local, H2; no hace falta instalar PostgreSQL.
- Para validar producción, PostgreSQL 16.

## Ejecutar localmente

Desde la raíz del repositorio, ejecutá `./gradlew bootRun`.

El perfil `local` se usa por defecto. La aplicación utiliza R2DBC con H2; Flyway usa JDBC para aplicar las migraciones al iniciar.

Health check: `http://localhost:8080/actuator/health`

Para ejecutar todas las pruebas: `./gradlew test`.

## Conexión a PostgreSQL

El perfil `prod` requiere estas variables:

| Variable | Uso |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Activa el perfil `prod`. |
| `DATABASE_URL` | URL JDBC para Flyway. |
| `R2DBC_URL` | URL R2DBC para persistencia reactiva. |
| `DATABASE_USERNAME` | Usuario de PostgreSQL. |
| `DATABASE_PASSWORD` | Contraseña de PostgreSQL. |

Ejemplo de valores para una instancia local de PostgreSQL:

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL=jdbc:postgresql://localhost:5432/choir`
- `R2DBC_URL=r2dbc:postgresql://localhost:5432/choir`
- `DATABASE_USERNAME=choir`
- `DATABASE_PASSWORD=change_me`

`.env.example` es una plantilla: Spring Boot no carga automáticamente un archivo `.env`. Exportá las variables en la shell o configurálas en el entorno de ejecución. No guardes credenciales reales en Git.

## Registro y correo de verificación

El registro de directores está **deshabilitado por defecto**. Para habilitarlo en un entorno desplegado hacen falta un servidor SMTP y la URL de la pantalla de verificación del frontend. La aplicación **no arranca** con el registro habilitado si falta alguno de estos datos.

| Variable | Uso | Valor por defecto |
|---|---|---|
| `CHOIR_AUTH_REGISTRATION_ENABLED` | Habilita el registro. | `false` |
| `CHOIR_AUTH_VERIFICATION_URL_BASE` | URL http(s) absoluta de la pantalla de verificación. El enlace del correo agrega `?token=...`. | vacío |
| `CHOIR_MAIL_ENABLED` | Activa el adaptador SMTP. | `false` |
| `CHOIR_MAIL_FROM` | Dirección del remitente. | sin valor |
| `CHOIR_MAIL_FROM_NAME` | Nombre visible del remitente. | `CHOIR` |
| `SPRING_MAIL_HOST` | Servidor SMTP. | sin valor |
| `SPRING_MAIL_PORT` | Puerto SMTP (1-65535). | sin valor |
| `SPRING_MAIL_USERNAME` | Usuario SMTP, si el servidor lo requiere. | sin valor |
| `SPRING_MAIL_PASSWORD` | Contraseña SMTP. Obligatoria si se define el usuario. | sin valor |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | Autenticación SMTP (`true`/`false`). | sin valor |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | Usa STARTTLS. | `true` |

Pasos para habilitar el registro:

1. Configurar las variables de `CHOIR_MAIL_*` y `SPRING_MAIL_*` con los datos del proveedor SMTP elegido, y `CHOIR_MAIL_ENABLED=true`.
2. Configurar `CHOIR_AUTH_VERIFICATION_URL_BASE` con la URL de la pantalla del frontend que reciba el parámetro `token` y llame a `POST /api/auth/verify-email`.
3. Configurar `CHOIR_AUTH_REGISTRATION_ENABLED=true`.

Si falta la configuración del paso 1 o 2, el arranque falla con un mensaje que indica qué propiedad falta, sin mostrar valores. Las credenciales se definen solo en el entorno de ejecución, nunca en Git.

El envío es síncrono, tiene tiempos máximos de conexión (5 s), lectura (5 s) y escritura (10 s), y no reintenta: si falla, el registro hace rollback y no se crea la cuenta. Las pruebas automáticas no abren conexiones SMTP ni necesitan credenciales.

## Migraciones

Las migraciones Flyway están en `src/main/resources/db/migration` y se aplican al iniciar. Flyway usa JDBC; la persistencia de runtime utiliza Spring Data R2DBC.

## CI

GitHub Actions ejecuta dos validaciones en `.github/workflows/ci.yml`:

- `test`: suite completa con el perfil local/H2.
- `test-prod`: PostgreSQL 16, perfil `prod`, migraciones Flyway y suite completa.

Ambos jobs repiten una prueba de registro concurrente para detectar regresiones.
El job `test-prod` también repite las dos suites de historial de evaluaciones
con bases PostgreSQL descartables para verificar su aislamiento y limpieza.

## Límites entre JDBC y R2DBC

- **R2DBC:** persistencia reactiva de runtime.
- **JDBC:** Flyway y pruebas o utilidades que lo requieran explícitamente.
- No agregar JDBC/JPA como alternativa para la persistencia de runtime.

## Documentación adicional

- [Modelo de dominio](docs/domain-model.md)
- [API de borradores de formaciones](docs/formation-drafts-api.md)
- [Specs de tickets CHOIR](docs/tickets/README.md)
