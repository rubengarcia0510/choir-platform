# CHOIR-32 — Vista de coreuta con sus posiciones y movimientos

- **Estado:** ⏳ Pendiente (Fase 5 — Ensayo y Visualización)
- **Fuente de verdad:** [CHOIR-32 en Jira](https://rubengarcia0510.atlassian.net/browse/CHOIR-32)
- **Dependencias técnicas previas:** CHOIR-30 (Modo ensayo), autenticación y permisos de coreuta
- **Desbloquea:** Portal móvil y de autoservicio para integrantes
- **Frontend consumidor:** `choir-platform-web` (CHOIR-32-FRONTEND — Vista individual del coreuta)

---

## 1. Objetivo y Alcance

Proveer una **vista de solo lectura personalizada para cada coreuta**, diseñada para uso en dispositivos móviles durante ensayos o antes de subir al escenario.

El coreuta autenticado puede consultar:
1. Las obras del concierto donde participa activamente.
2. Sus coordenadas y sector en cada formación.
3. El vector de movimiento hacia su siguiente posición cuando cambia de canción, si existe una transición publicada.

---

## 2. Contrato HTTP

El endpoint propuesto por esta especificación es:

`GET /api/me/concerts/{concertId}/my-positions`

La autenticación debe usar el mecanismo configurado por la aplicación. Confirmar con Jira y la implementación de seguridad si el endpoint requiere un rol de coreuta específico. No añadir headers ficticios ni exponer credenciales en documentación o código.

La forma de respuesta debe alinearse con los modelos y unidades reales de coordenadas del dominio. El ejemplo siguiente es ilustrativo; no establece por sí solo un contrato para `row`, `column` o instrucciones narrativas:

```json
{
  "singer": {
    "id": 12,
    "name": "Pedro Albornoz",
    "voiceSection": "Tenor 1"
  },
  "repertoire": [
    {
      "order": 1,
      "songTitle": "Ave Verum Corpus",
      "position": { "x": 30.0, "y": 20.0 },
      "nextTransition": {
        "targetSongTitle": "Lacrimosa",
        "movementVector": { "deltaX": 10.0, "deltaY": 0.0 }
      }
    }
  ]
}
```

---

## 3. Criterios de Aceptación

- [ ] Aislamiento de seguridad: el coreuta solo puede consultar sus propios datos.
- [ ] No permite ninguna operación de mutación (solo `GET`).
- [ ] Muestra únicamente conciertos y formaciones con versiones publicadas/aprobadas por el director.
- [ ] Las coordenadas usan las mismas unidades y orientación documentadas por el dominio.
- [ ] El contrato y el rol requerido quedan confirmados antes de integrar el frontend.
