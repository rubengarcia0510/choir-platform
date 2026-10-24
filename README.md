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

## Migraciones

Las migraciones Flyway están en `src/main/resources/db/migration` y se aplican al iniciar. Flyway usa JDBC; la persistencia de runtime utiliza Spring Data R2DBC.

## CI

GitHub Actions ejecuta dos validaciones en `.github/workflows/ci.yml`:

- `test`: suite completa con el perfil local/H2.
- `test-prod`: PostgreSQL 16, perfil `prod`, migraciones Flyway y suite completa.

Ambos jobs repiten una prueba de registro concurrente para detectar regresiones.

## Límites entre JDBC y R2DBC

- **R2DBC:** persistencia reactiva de runtime.
- **JDBC:** Flyway y pruebas o utilidades que lo requieran explícitamente.
- No agregar JDBC/JPA como alternativa para la persistencia de runtime.

## Documentación adicional

- [Modelo de dominio](docs/domain-model.md)
- [API de borradores de formaciones](docs/formation-drafts-api.md)
