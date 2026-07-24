# ADR 0002: `ParkingSession.plateSnapshot` como fuente de verdad para salida por patente

## Estado

Aceptado (2026-07-14)

## Contexto

[[0001-resolucion-acceso-patente-ticket]] estableció que una patente no registrada como `Vehicle` nunca abre la barrera, ni en entrada ni en salida — el visitante debe usar el tótem (`TICKET`).

Surgió un caso no cubierto: un visitante puede, durante su estadía, asociar su patente a la sesión/ticket ya abierto (mecanismo todavía no definido — reconocimiento automático al re-escanear, QR, carga manual, etc.). Una vez asociada, debería poder salir escaneando la patente, sin necesitar el ticket físico.

## Decisión

`PlateAccessStrategy` no distingue "vehículo registrado" de "visitante con patente asociada" — ambos casos se resuelven de la misma forma, contra `ParkingSession.plateSnapshot`:

1. Buscar `ParkingSession` `ACTIVE` con `plateSnapshot = identifier` → si existe, `EXIT` (`session.complete(...)`), sin importar si el `sessionType` es `REGISTERED` o `VISITOR`. Esto ya funciona hoy para `VISITOR` **si** en algún momento se le seteó `plateSnapshot` a la sesión — la strategy no necesita saber cómo se asoció.
2. Si no hay sesión activa con esa patente → buscar `Vehicle` activo con `plate = identifier`:
   - Existe → `ENTRY`, se crea `ParkingSession.forRegisteredUser(...)`.
   - No existe → `AccessResult.DENIED`. No se crea sesión (un visitante no puede "entrar" por patente, solo por tótem, per ADR 0001).

**Mecanismo de asociación patente↔sesión-visitante: fuera de alcance de este ADR**, igual que `PROXIMITY_KEY` en ADR 0001 — necesita su propio diseño (endpoint, UI del tótem o QR) antes de implementarse. Mientras no exista, `VISITOR` sessions quedan con `plateSnapshot = null` y por lo tanto nunca resuelven por `PLATE`, solo por `TICKET` — consistente con el comportamiento actual.

**Caso DENIED sin contexto (patente no registrada, sin sesión que consultar):** no hay forma de inferir `eventType` (`AccessLog.eventType` es obligatorio, no admite null, y no hay sesión de la cual derivarlo). Se decide loguear `ENTRY` por defecto en este caso puntual — es una imprecisión de auditoría menor y aceptada: el intento ya quedó registrado como `DENIED`, y este es el escenario menos común (la mayoría de los intentos denegados son en la entrada, un visitante rara vez intenta salir escaneando patente porque se le indicó usar el ticket).

## Consecuencias

- `ParkingSessionRepository` necesita `findByPlateSnapshotAndStatus(String plateSnapshot, SessionStatus status)` (ya señalado en ADR 0001, ahora con un segundo uso: cubre tanto `REGISTERED` como `VISITOR` con patente asociada).
- `VehicleRepository` necesita `findByPlate(String plate)`.
- El día que se implemente la asociación patente↔ticket, no hace falta tocar `PlateAccessStrategy` — alcanza con que ese nuevo flujo setee `plateSnapshot` en la `ParkingSession` existente.
