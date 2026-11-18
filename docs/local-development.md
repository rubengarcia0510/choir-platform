# Local development

## Create a local development director

The initializer is disabled by default and only runs with the local Spring profile and explicit opt-in.

Required environment variables:

- `CHOIR_AUTH_LOCALDIRECTOR_ENABLED=true`
- `CHOIR_DEV_DIRECTOR_NAME`
- `CHOIR_DEV_DIRECTOR_EMAIL`
- `CHOIR_DEV_DIRECTOR_PASSWORD`

The director name must contain between 1 and 160 characters.
The email must be valid and contain at most 254 characters.
The password must contain between 12 and 200 characters.

Example startup:

    export SPRING_PROFILES_ACTIVE=local
    export CHOIR_AUTH_LOCALDIRECTOR_ENABLED=true
    export CHOIR_DEV_DIRECTOR_NAME="Local Director"
    export CHOIR_DEV_DIRECTOR_EMAIL="director@example.test"
    export CHOIR_DEV_DIRECTOR_PASSWORD="replace-with-a-local-password"
    ./gradlew bootRun

Use a local-only password. Never commit credentials.

The password is encoded with BCrypt. Existing directors are not modified.
The initializer is unavailable in production, even if the enabling property is set to true.
