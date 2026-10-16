# CHOIR Platform

Backend de CHOIR, una plataforma para que directores de coro organicen coreutas, conciertos y formaciones.

## Requisitos

- JDK 21.
- Git.
- Para desarrollo local no hace falta instalar PostgreSQL: el perfil `local` usa H2.
- Para validar el perfil de producción, PostgreSQL 16 accesible.

## Ejecutar localmente

Desde la raíz del repositorio:

```bash
./gradlew bootRun

El perfil "local" se usa por defecto. La persistencia de la aplicación utiliza R2DBC con H2; Flyway usa JDBC para aplicar las migraciones SQL al iniciar.

Health check: http://localhost:8080/actuator/health

Para ejecutar todas las pruebas:

./gradlew test

Conexión a PostgreSQL

El perfil "prod" requiere estas variables de entorno:

Variable| Uso
"SPRING_PROFILES_ACTIVE=prod"| Activa el perfil de producción.
"DATABASE_URL"| URL JDBC para las migraciones Flyway.
"R2DBC_URL"| URL R2DBC para la persistencia reactiva.
"DATABASE_USERNAME"| Usuario de PostgreSQL.
"DATABASE_PASSWORD"| Contraseña de PostgreSQL.

Ejemplo para una instancia local de PostgreSQL:

export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL='jdbc:postgresql://localhost:5432/choir'
export R2DBC_URL='r2dbc:postgresql://localhost:5432/choir'
export DATABASE_USERNAME='choir'
export DATABASE_PASSWORD='change_me'
./gradlew bootRun

".env.example" es una plantilla, no un archivo de configuración que Spring Boot cargue automáticamente. Exportá las variables en la shell o configurálas en el entorno de ejecución. No guardes credenciales reales en Git.

Migraciones

Las migraciones Flyway están en "src/main/resources/db/migration" y se aplican al iniciar la aplicación. Flyway usa JDBC; las consultas y escrituras de runtime deben usar Spring Data R2DBC.

CI

GitHub Actions ejecuta dos validaciones en ".github/workflows/ci.yml":

- "test": suite completa con el perfil local/H2.
- "test-prod": PostgreSQL 16, perfil "prod", migraciones Flyway y suite completa con la configuración R2DBC de PostgreSQL.

Ambos jobs repiten la prueba concurrente de registro para detectar regresiones intermitentes. Revisá el resultado de CI del PR antes de integrar cambios.

Límites entre JDBC y R2DBC

- R2DBC: persistencia reactiva de runtime.
- JDBC: Flyway y pruebas o utilidades que lo requieran explícitamente.
- No agregues JDBC/JPA como alternativa para la persistencia de runtime.

Documentación adicional

- "Modelo de dominio" (docs/domain-model.md)
  EOF

cat > .env.example <<'EOF'

Example PostgreSQL settings for the prod profile.

Placeholders only; never commit real credentials.

Spring Boot does not automatically load a generic .env file.

SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://localhost:5432/choir
R2DBC_URL=r2dbc:postgresql://localhost:5432/choir
DATABASE_USERNAME=change_me
DATABASE_PASSWORD=change_me
