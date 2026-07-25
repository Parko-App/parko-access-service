# ADR 0001: Resolución de acceso por patente (PLATE) y ticket (TICKET)

## Estado

Aceptado (2026-07-14)

## Contexto

`parko-access-service` recibe intentos de acceso desde 3 fuentes físicas distintas (proceso Python de escaneo de patente, tótem de visitantes, llave de proximidad), unificadas en un solo endpoint que despacha por `AccessMethod` (`PLATE`, `PROXIMITY_KEY`, `TICKET`) vía Strategy.

El `eventType` (`ENTRY`/`EXIT`) no lo manda el dispositivo — lo determina el server en base a si existe una `ParkingSession` activa para el vehículo/ticket en cuestión, para no confiar en un dispositivo que podría mentir.

Faltaba definir la regla de negocio real para `PLATE` y `TICKET`: quién puede abrir la barrera y bajo qué condición. `PROXIMITY_KEY` queda fuera de este ADR — no tiene modelo de dominio todavía (sin entidad que vincule una llave física a un `Vehicle`/`User`).

## Decisión

**PLATE (solo vehículos registrados):**

- La barrera **solo abre para patentes que existen como `Vehicle` activo en la base**, tengan saldo (`BalanceAccount`) o no — el saldo es irrelevante para la apertura, se cobra/descuenta aparte.
- Patente no registrada (visitante) → `AccessResult.DENIED`, la barrera **no** abre. Esto vale tanto para intento de entrada como de salida — un visitante nunca abre la barrera escaneando patente, en ningún sentido.
- Resolución del `eventType` para una patente registrada:
  - No hay `ParkingSession` `ACTIVE` con ese `plateSnapshot` → `ENTRY`, se crea `ParkingSession.forRegisteredUser(...)`.
  - Hay una `ParkingSession` `ACTIVE` con ese `plateSnapshot` → `EXIT`, se llama `session.complete(...)`.

**TICKET (solo visitantes):**

- El visitante aprieta el botón del tótem → esto **es** el evento de entrada: se crea `ParkingSession.forVisitor(...)` (`ENTRY`, sin `vehicleId`/`plateSnapshot`) y en la misma operación se emite el `Ticket` (`parkingSessionId` de la sesión recién creada) → la barrera abre. En el `AccessRequest` esto se representa con `identifier` vacío/nulo y `accessMethod = TICKET` (no hay ticket todavía, se está pidiendo uno nuevo).
- Salida: se escanea el ticket (`identifier` = `ticketNumber`/`qrData`) →
  - Ticket no existe, o existe pero **no está `PAID`** → `AccessResult.DENIED`, la barrera no abre.
  - Ticket existe y está `PAID` → `ticket.markAsUsed()` + `session.complete(...)` → `AccessResult.AUTHORIZED`, `EXIT`, barrera abre.
- Igual que con `PLATE`: la patente del visitante nunca sirve para salir, únicamente el ticket pagado.

**Guardrail de implementación:** `Ticket.markAsUsed()` tira `IllegalStateException` si el estado no es `PAID` — eso es una violación de invariante de dominio, no una denegación de acceso normal. Cada strategy debe capturar esos estados esperados (ticket no pagado, patente no encontrada, sesión inexistente) **antes** de invocar el método de dominio que tira la excepción, y traducirlos a un `AccessResult.DENIED` con HTTP 200 — no dejar que caigan al `GlobalExceptionHandler` genérico (que devolvería 500 para un caso de negocio normal).

## Consecuencias

- `AccessRequest` no necesita `eventType` — lo infiere cada strategy según exista o no sesión/ticket activo.
- `ParkingSessionRepository` (persistence-core) necesita un query method nuevo, `findByPlateSnapshotAndStatus(String plateSnapshot, SessionStatus status)` — hoy es `JpaRepository` pelado sin queries derivadas. Cambio en el repo `parko-persistence-core`, no en `access-service`.
- `TicketRepository` (persistence-core, si no existe todavía) necesita lookup por `ticketNumber`/`qrData`.
- Denegar acceso (patente no registrada, ticket no pagado) es un resultado de negocio válido, siempre `200 OK` con `AccessResult.DENIED` en el body — nunca un error HTTP.
- `PROXIMITY_KEY` queda explícitamente fuera de alcance: sin modelo de dominio (`ProximityKey`/vínculo a `Vehicle`/`User`), no se puede resolver sesión activa para ese método todavía. Requiere ADR propio antes de implementarse.
