# parko-access-service

Artefacto para la gestión de control de acceso de vehículos al estacionamiento (puerto `8081`).

## Setup local (repo recién clonado)

### 1. Requisitos

- Java 21.
- Docker (para Postgres y Keycloak locales).
- Cuenta de GitHub con acceso a la org `Parko-App` (los packages son privados).

### 2. Auth para GitHub Packages

El `pom.xml` baja `parko-domain-lib` y `persistence.core` desde GitHub Packages, no desde Maven Central. Sin credenciales, `mvnw` falla con 401/403.

Agregar a `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github-domain-lib</id>
      <username>TU_USUARIO_GITHUB</username>
      <password>TU_TOKEN_CON_read:packages</password>
    </server>
    <server>
      <id>github-persistence-core</id>
      <username>TU_USUARIO_GITHUB</username>
      <password>TU_TOKEN_CON_read:packages</password>
    </server>
  </servers>
</settings>
```

El token necesita el scope `read:packages` y el usuario debe tener acceso a los repos `parko-domain-lib` y `parko-persistence-core`.

Alternativa sin tocar `~/.m2`: usar el settings del CI con variables de entorno.

```bash
GITHUB_ACTOR=tu_usuario PACKAGES_READ_TOKEN=tu_token ./mvnw -s .github/maven-settings.xml clean install
```

### 3. Postgres

```bash
docker run -d --name parko-access-postgres -p 5432:5432 \
  -e POSTGRES_DB=parko_db -e POSTGRES_USER=user -e POSTGRES_PASSWORD=password \
  postgres:16-alpine
```

(o exportar `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` apuntando a otra instancia). El esquema se crea solo (`ddl-auto: update`), no hace falta migración manual.

### 4. Keycloak

El repo trae `docker-compose.yml` con Keycloak + el realm `Parko` ya preparado en `keycloak/realm-export.json` (client `access-service`, service account habilitada).

```bash
docker compose up -d keycloak
```

Confirmar en `docker compose logs keycloak` que dice algo como `Realm 'Parko' imported`. Solo importa la primera vez (si el realm ya existe en el volumen, no pisa cambios).

**Antes de correr la app**, sacar el secret real del client (el export lo trae enmascarado, Keycloak genera uno nuevo al importar):

1. Entrar a `http://localhost:8180` (admin / admin, o lo que hayas puesto en `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`).
2. Realm `Parko` → Clients → `access-service` → pestaña **Credentials** → copiar el secret.

### 5. Variables de entorno

Sin default, la app no arranca sin esto:

```bash
export KEYCLOAK_CLIENT_ID=access-service
export KEYCLOAK_CLIENT_SECRET=<secret del paso anterior>
```

El resto tiene default apuntando a `localhost` (`DB_URL`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_TOKEN_URI`, `BALANCE_SERVICE_URL`, `PARKING_ENTRY_FEE`) — pisar solo si tu setup difiere.

### 6. Levantar la app

```bash
./mvnw spring-boot:run
```

### 7. balance-service (opcional, solo para el flujo de cobro completo)

Los endpoints de `/api/v1/access/**` que descuentan saldo llaman a `parko-balance-service` vía Feign, autenticado con un token propio (`client_credentials` contra Keycloak). Para probar ese flujo de punta a punta hace falta clonar y levantar también `parko-balance-service` (con su propio Keycloak y setup — ver su README). Sin eso, la app compila y arranca igual, pero esas llamadas van a fallar.
