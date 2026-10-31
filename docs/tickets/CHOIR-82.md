# CHOIR-82 — Incorporar CI para choir-platform-web

- Estado al snapshot: Por hacer.
- Fuente: [CHOIR-82 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-82).
- Repositorio indicado por Jira: `choir-platform-web`; confirmar repositorio de
  implementación antes de iniciar este ticket desde `choir-platform`.

## Objetivo y alcance

Agregar GitHub Actions para PRs hacia `main` y pushes a `main`, con instalación
reproducible usando `npm ci`, pruebas `npx ng test --watch=false` y build
`npx ng build`. Revisar cómo se sirven/publican las páginas estáticas de
`docs/` y conservar el mecanismo actual.

## Criterios de aceptación

- El workflow se ejecuta en PRs a `main` y pushes a `main`.
- La instalación usa el lockfile con `npm ci`.
- El job ejecuta tests y build, y falla si alguno falla.
- Las páginas estáticas actuales siguen accesibles; CI no elimina, reemplaza
  ni altera su mecanismo de publicación/servicio.
- Identificar y conservar cualquier configuración actual de GitHub Pages u
  otro workflow de publicación. No mezclar cambios de despliegue salvo que sea
  imprescindible y esté documentado.
- No agregar secretos/credenciales.
- Documentar brevemente la configuración en el repositorio o PR.
- Validar Actions y que las URLs vigentes de `docs/` sigan funcionando.

## Fuera de alcance

No cambiar diseño/contenido de páginas, hosting o URLs; no habilitar merge
automático; no introducir cambios funcionales de CHOIR-21.
