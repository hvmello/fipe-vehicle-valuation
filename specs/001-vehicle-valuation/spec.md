# Spec 001 — Vehicle Valuation Over Manufacture Years

**Status:** Draft (awaiting verification)
**Owner:** —
**Related:** [data-model.md](./data-model.md) · [api-contract.md](./api-contract.md) · [tasks.md](./tasks.md)

## 1. Problem & Goal

Consumers of the FIPE table want to understand how a specific vehicle's average market
price evolved across the years it was manufactured. Given a **brand** and a **model**, the
system must return, for every manufacture year, the FIPE price plus the **absolute and
percentage variation relative to the previous available year**, newest year first.

This is the literal requirement of the technical test, including the worked example:

```
Veículo XPTO fabricado em 2009, 2010, 2011, 2013:
- 2013 -> R$25.000,00, alteração de R$2.500,00 (11%) em relação a 2011
- 2011 -> R$22.500,00, alteração de R$2.250,00 (11%) em relação a 2010
- 2010 -> R$20.250,00, alteração de R$2.025,00 (11%) em relação a 2009
- 2009 -> R$18.225,00
```

Note that **2012 is absent**, so 2013's variation is computed against **2011** (the previous
*available* year), not a literal `year − 1`.

## 2. Scope

### In scope
- REST microservice (Java 17, Spring Boot) consuming FIPE v2.
- Endpoints to list brands, list models, and compute the valuation history.
- Support for the three FIPE vehicle types: `cars`, `motorcycles`, `trucks`.
- Angular 18 screen to pick type → brand → model and render the result.

### Out of scope
- Authentication / user accounts.
- Persisting results to a database (FIPE is the source; a short in-memory cache is allowed).
- Historical reference months other than what FIPE currently returns.

## 3. Personas & User Stories

- **US-1 (Evaluator/end user):** As a user, I select a vehicle type, brand and model so that
  I can see how its price changed over the manufacture years.
- **US-2 (API consumer):** As a client app, I call a single endpoint with type + brand id +
  model id and receive the full year-by-year valuation, already computed and ordered.

## 4. Functional Requirements (acceptance criteria)

Each requirement has a stable id. **Every automated test must reference the id it verifies.**

| Id | Given / When / Then |
|----|---------------------|
| **FR-1** | **Given** a model whose years are `[2009, 2010, 2011, 2013]`, **When** the valuation is requested, **Then** the response lists entries **newest-first** (`2013, 2011, 2010, 2009`), each (except the oldest) carrying `change` and `changePercent` computed against the **previous available** year — i.e. 2013 vs 2011. |
| **FR-2** | **Given** the oldest year in the list, **Then** its `change`, `changePercent` and `previousYear` are `null` (no prior year to compare). |
| **FR-3** | **Given** a model with a single manufacture year, **Then** the response has exactly one entry with `null` change fields. |
| **FR-4** | **Given** a FIPE price string such as `"R$ 6.027,00"` or `"R$ 1.234.567,89"`, **Then** it is parsed to an exact `BigDecimal` (`6027.00`, `1234567.89`) — money is never stored as `double`/`float`. |
| **FR-5** | **Given** a non-existent brand or model id, **When** any endpoint is called, **Then** the API responds **404** with an RFC-7807 ProblemDetail body — never a 500. |
| **FR-6** | **Given** a FIPE year entry whose `modelYear == 32000` (FIPE's "zero km" sentinel), **Then** it is labeled **"0 km"** and ordered as the **newest** entry. |
| **FR-7** | **Given** `GET /{type}/brands`, **Then** the API returns the list of brands (`{id, name}`) for that vehicle type, suitable for a dropdown. |
| **FR-8** | **Given** `GET /{type}/brands/{brandId}/models`, **Then** the API returns the list of models (`{id, name}`) for that brand. |
| **FR-9** | **Given** the valuation endpoint, **Then** the response includes vehicle metadata: `brand`, `model`, `fipeCode`, `referenceMonth`, `currency = "BRL"`. |
| **FR-10** | **Given** the percentage calculation, **Then** `changePercent = change / previousPrice × 100`, rounded **HALF_UP to 2 decimals**, and `change = price − previousPrice` (2 decimals). |
| **FR-11** | **Given** a vehicle type outside `{cars, motorcycles, trucks}`, **Then** the API responds **400** ProblemDetail. |
| **FR-12** | **Given** FIPE is unreachable, times out, or returns 5xx/**429** (rate limit), **Then** the API responds **502/503** ProblemDetail (degrades gracefully, no stack trace leak; 429 → 503 mentioning rate limiting). |
| **FR-13** | **Given** a model with the same year in two fuels (verified: VW Fusca `1996-1 Gasolina` + `1996-2 Álcool`), **When** valuation is requested, **Then** entries are ordered `(modelYear asc, fuelId desc)` and chained **sequentially regardless of fuel** (deterministic); the newest-first output is `1996 Gasolina, 1996 Álcool, 1995 Gasolina, 1995 Álcool, …`, `previousYear` may equal `year`, and `previousLabel` names the compared entry. |
| **FR-14** | **Given** any of our endpoints (brands, models, valuation), **Then** the response **explicitly echoes the human-readable `vehicleType`** (`cars`/`motorcycles`/`trucks`), because FIPE's list endpoints omit it and its price object only exposes an opaque integer. The service maps that integer (verified `1=cars, 2=motorcycles, 3=trucks`) and asserts it matches the requested type. |

## 5. UI Acceptance Criteria (Angular)

| Id | Criterion |
|----|-----------|
| **UI-1** | Three dependent dropdowns: **Tipo** → **Marca** → **Modelo**. Selecting a type loads brands; selecting a brand loads models; selecting a model enables **Consultar**. |
| **UI-2** | While any request is in flight a spinner is shown; the trigger control is disabled. |
| **UI-3** | Results render **newest-first** with: year label, price (`R$` pt-BR), and for non-oldest rows `±R$X (Y%) em relação a {previousYear}`, with **green** for positive and **red** for negative variation. |
| **UI-4** | The oldest row shows the value only (no variation). A `0 km` row is labeled as such. |
| **UI-5** | On API error, a friendly message is shown (no raw stack trace); the form remains usable. |
| **UI-6** | A results header shows brand, model, FIPE code and reference month. |

## 6. Non-Functional Requirements

- **NFR-1 Money correctness:** all monetary math uses `BigDecimal`; rounding `HALF_UP`, scale 2.
- **NFR-2 Concurrency:** per-year price lookups are fetched concurrently (bounded), not sequentially.
- **NFR-3 Resilience:** per-call timeouts; FIPE 5xx mapped to gateway errors; optional short retry/backoff.
- **NFR-4 Caching:** brands/models/years/prices cached in-memory (FIPE updates monthly, so values
  are stable within a reference month). Beyond latency, caching is **required to respect rate
  limits** (NFR-8): one valuation makes **N price calls** (one per model-year), so an uncached
  multi-year lookup burns N requests against the daily quota.
- **NFR-5 Java bytecode target 17**; builds with Maven.
- **NFR-6 Observability:** meaningful logs on FIPE failures; OpenAPI/Swagger UI exposed.
- **NFR-7 CORS:** the Angular dev origin (`http://localhost:4200`) is permitted.
- **NFR-8 Rate limits & token:** per the provider docs (fipe.online), the FIPE API allows
  **500 req/day unauthenticated, 1,000/day with a free token, unlimited on a paid plan**. The
  client therefore (a) sends the optional `X-Subscription-Token` when `fipe.subscription-token`
  is configured, and (b) leans on the cache (NFR-4) and bounded concurrency (NFR-2) to stay well
  under the cap. Quota-exhaustion responses (HTTP 429) are mapped to a 503 ProblemDetail.

## 7. Assumptions & Decisions (each verified against the live API)

- **Vehicle = FIPE model.** FIPE additionally requires a **vehicle type**, exposed as the first
  selectable parameter (default UX: Carros). *(design decision)*
- **Year code = `<modelYear>-<fuelId>`.** Verified. fuelId mapping observed live:
  `1=Gasolina, 2=Álcool, 4=Elétrico, 5=Flex, 6=Híbrido` (`3=Diesel`, FIPE standard).
- **0 km = `modelYear 32000`.** Verified live (Fiat Argo Flex → `"modelYear":32000`); labeled "0 km", sorted newest.
- **`vehicleType` integer mapping verified live:** `1=cars, 2=motorcycles, 3=trucks`. FIPE only
  exposes it (as an int) in the price object — **list endpoints omit type entirely** — so our API
  **echoes a human-readable `vehicleType` in every response** (FR-14) and uses the int for an
  integrity check.
- **Same-year multi-fuel exists.** Verified live (VW Fusca model 2380: `1996` and `1995` each in
  Gasolina **and** Álcool). **Decision:** chain **sequentially by year, mixed fuel** (FR-13) —
  ordered `(modelYear asc, fuelId desc)`, each compared to the previous element regardless of
  fuel. A deliberate, documented simplification (over per-fuel series); `previousLabel` keeps the
  cross-fuel case legible. Single-fuel models (the example + the majority) are unaffected.
- **Price always a non-empty pt-BR string** in 2xx responses (verified). Parsed to `BigDecimal`.
- **FIPE returns years newest-first**, but we **re-sort** ourselves and never depend on upstream order.
- **Percentage:** API keeps 2 decimals (`11.11`); the UI rounds to whole `%`. *(decision)*
- **Reference month / token:** the client supports the optional `reference` (past-month) query
  param and `X-Subscription-Token` header, but by default queries the **latest** table with **no
  UI selector** — keeps scope on the test requirement. Full upstream contract (10 endpoints) is in
  api-contract.md Part B; the month-history endpoint (#10) is intentionally **not** used (wrong axis).
- **Upstream 404 verified:** bad ids return HTTP 404 with body `{ "error": "failed to locate the
  information on fipe.org" }` (single `error` string — no `status`/`type`). The client still maps
  **by HTTP status** (`NotFound` → 404, 5xx/timeout → 502/503); the `error` text may be surfaced
  in our ProblemDetail `detail`, but mapping never depends on the body.

## 8. Open Questions

- None. All assumptions above were validated against live FIPE responses; multi-fuel handling and
  precision are decided (FR-13, §4).

## 9. Traceability

Every FR/UI id is mapped to a task in [tasks.md](./tasks.md) and to at least one automated
test. A requirement is "met" only when its referencing test is green and code review clears.
