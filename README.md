# FIPE Vehicle Valuation

[![CI](https://github.com/hvmello/fipe-vehicle-valuation/actions/workflows/ci.yml/badge.svg)](https://github.com/hvmello/fipe-vehicle-valuation/actions/workflows/ci.yml)

A microservice (**Java 17 / Spring Boot, WebFlux**) that consumes the
[FIPE v2 API](https://fipe.online/docs/api/fipe) and, given a **vehicle type + brand + model**,
returns each manufacture year's price plus the **absolute and percentage variation versus the
previous available year**, newest first — with an **Angular 18 + Material** screen to drive it.

> Worked example (from the brief): a model made in 2009/2010/2011/2013 →
> `2013 → R$25.000,00 (+R$2.500,00, 11% vs 2011)`, `2011 → +R$2.250,00 (11% vs 2010)`, … ,
> `2009 → base value`. Note 2012 is absent, so 2013 compares to **2011** (the previous *available* year).

The design is **spec-driven**: the source of truth is [`specs/001-vehicle-valuation/`](specs/001-vehicle-valuation)
(functional spec, data model, API contract, tasks), and every test references the requirement id it verifies.
The design choices (and what they were chosen against) are explained in plain language in
[`decisions.html`](decisions.html); progress is tracked in [`progress.html`](progress.html).

## Architecture

```
Angular 18 (Material)  ──HTTP /api/v1──▶  Spring Boot service  ──HTTP──▶  FIPE v2 API
  type→brand→model UI                     valuation + caching             (parallelum)
```

- **Backend** (`backend/`): reactive `WebClient`, concurrent per-year price fetch, `BigDecimal`
  money math, RFC-7807 error responses, Caffeine caching. See package `com.fipe.valuation`.
- **Frontend** (`frontend/`): standalone components, Signals, Angular Material, typed `HttpClient`.

## Prerequisites

- **JDK 17+** (built/tested on Temurin 21; bytecode targets 17).
- **Node 20+ / npm**, and **Google Chrome** (for `ng test`).
- **No Maven install needed** — the project ships the **Maven Wrapper** (`./mvnw`), which fetches the
  right Maven version automatically. (If you already have `mvn`/`mvnd`, those work too.)

### Corporate TLS proxy note (only if `npm install` fails with a certificate error)

If your network uses a TLS-inspecting proxy, Node won't trust it by default
(`UNABLE_TO_VERIFY_LEAF_SIGNATURE`). The secure fix — **no `strict-ssl` downgrade** — is to point
Node at the root CAs your OS already trusts:

```powershell
# Export the Windows trusted roots to a PEM and trust them in Node (verification stays ON):
$pem = "$env:USERPROFILE\.node-ca\win-root-ca.pem"
# (one-time export of Cert:\LocalMachine\Root + CurrentUser\Root to $pem, then:)
[Environment]::SetEnvironmentVariable('NODE_EXTRA_CA_CERTS', $pem, 'User')
```

The **backend** (Java) is the same story for its outbound calls to FIPE. On Windows, make the JVM
trust the OS root store (no truststore edits):

```powershell
$env:MAVEN_OPTS = "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
mvnd spring-boot:run "-Dspring-boot.run.jvmArguments=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT"
```

(On a normal/un-proxied network none of this is needed — `mvnd spring-boot:run` and `npm install` just work.)

## Run the backend

```bash
cd backend
./mvnw spring-boot:run        # starts on http://localhost:8080  (mvnw.cmd on Windows)
```

Sample request (newest-first valuation for Fiat 147):

```bash
curl "http://localhost:8080/api/v1/cars/brands/21/models/437/valuation"
```

Interactive API docs (Swagger UI): **http://localhost:8080/swagger-ui.html** · OpenAPI JSON at `/v3/api-docs`.

Operational endpoints (Actuator): **`/actuator/health`** and **`/actuator/metrics`**. The FIPE cache is
observable via `/actuator/metrics/cache.gets` (tags `cache=fipe.catalog` | `fipe.prices`), exposing
hit/miss/eviction counters.

Endpoints (`/api/v1`):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/{type}/brands` | list brands (`type` = `cars`/`motorcycles`/`trucks`) |
| GET | `/{type}/brands/{brandId}/models` | list models |
| GET | `/{type}/brands/{brandId}/models/{modelId}/valuation` | year-by-year valuation |

Optional config (env): `FIPE_SUBSCRIPTION_TOKEN` (sent only if set). Other settings under the
`fipe.*` prefix in `backend/src/main/resources/application.yml`.

## Run the frontend

```bash
cd frontend
npm install
npm start                     # http://localhost:4200, proxies /api → :8080
```

Then pick **Tipo → Marca → Modelo** and press **Consultar**. (Run the backend first.)

## Tests

```bash
cd backend  && ./mvnw test        # 56 tests (unit, service, controller, integration, security, cache, references)
cd frontend && npm run test:ci    # 12 specs (service + components), headless Chrome
```

Both suites run automatically on every push/PR via [GitHub Actions](.github/workflows/ci.yml).

## Notable design decisions

- **Money is `BigDecimal`** (scale 2, HALF_UP) end to end — never floating point.
- **Variation chains the previous *available* year** (handles gaps like the missing 2012).
- **Sequential, mixed-fuel** chaining for same-year multi-fuel models (e.g. VW Fusca
  `1996 Gasolina` + `1996 Álcool`); a `previousLabel` keeps cross-fuel comparisons legible.
- **`modelYear 32000` = "0 km"**, sorted as newest (verified live).
- **`vehicleType` echoed in every response** (FIPE omits it from list endpoints).
- Concurrency-bounded fan-out + a **two-tier Caffeine cache** keep us under FIPE's daily rate limit:
  - **catalog** (brands/models/years) — stable, reference-independent → long TTL (default **7d**).
  - **price** — changes monthly → moderate TTL (**24h**) and its key carries FIPE's current
    **reference-table code**. A `CurrentReferenceProvider` resolves that code at startup and re-checks
    daily; when FIPE publishes a new month it **triggers an invalidation** of the price cache (freeing
    the old month immediately) and subsequent reads fetch fresh prices — TTL is only a safety net.
  - Both caches are **size-bounded**, so Caffeine's frequency-aware (W-TinyLFU) eviction keeps the hot
    working set resident and drops cold vehicles (RAM-efficient — we never prefetch the whole catalog).
  - All tunable under `fipe.cache.*` in `application.yml`; instrumented via Micrometer (meters
    `fipe.catalog` / `fipe.prices`, see Actuator above).
- **Hardened transport**: CORS restricted to the configured origin, safe methods (GET/OPTIONS) and an
  explicit header allow-list (no wildcard); baseline security headers (`X-Content-Type-Options`,
  `X-Frame-Options`, `Referrer-Policy`) on every response.
- **pt-BR currency parsing** via a locale-pinned `DecimalFormat` (no regex), with strict
  full-consumption validation so malformed amounts are rejected, not silently truncated.
- **Swagger UI** (springdoc) at `/swagger-ui.html`; the full contract is also in `specs/.../api-contract.md`.

See [`decisions.html`](decisions.html) for the reasoning behind each choice, in plain terms.

## License

Released under the [MIT License](LICENSE).
