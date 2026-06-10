# Spec 001 — API Contract

> ⚠️ **Two different APIs live in this document — don't conflate them:**
> | | **Part A — OUR microservice** | **Part B — UPSTREAM FIPE** |
> |---|---|---|
> | Who builds it | us (Spring Boot) | third party (parallelum) |
> | Base | `/api/v1` *(our own version, unrelated to FIPE's "v2")* | `https://fipe.parallelum.com.br/api/v2` |
> | Role | what Angular calls | what our service calls internally |
> | Source of truth | this spec (we design it) | the official OpenAPI (we just consume it) |
>
> The **`v1` in Part A is our service's own version** and has nothing to do with FIPE being on
> `v2`. They are independent. (Open question: keep `/api/v1` or drop the version — see end of file.)

This document has **two parts**:
- **Part A — Our service API** (`/api/v1/...`): what we expose to the Angular app. This is the
  contract the springdoc OpenAPI (`/v3/api-docs`) must mirror.
- **Part B — Upstream FIPE v2 API** (consumed): **primary source = the FIPE docs at
  `https://fipe.online/docs/api/fipe`**, corroborated by live responses. The GitHub OpenAPI
  (`parallelum/fipe-csharp`) is used only as a secondary cross-check — and it proved *incomplete*
  (missing endpoints #6/#7), so the primary docs + live data take precedence wherever they differ.

---

# Part A — Our service API

Base path: `/api/v1`. Content type: `application/json`. Errors: `application/problem+json`.

`{type}` ∈ `cars | motorcycles | trucks` (case-insensitive). Invalid → **400**.

---

## 1. List brands — FR-7

```
GET /api/v1/{type}/brands
```
**200** — each item echoes `vehicleType` (FR-14; FIPE omits it from list endpoints)
```json
[
  { "vehicleType": "cars", "id": "1", "name": "Acura" },
  { "vehicleType": "cars", "id": "21", "name": "Fiat" }
]
```

---

## 2. List models — FR-8

```
GET /api/v1/{type}/brands/{brandId}/models
```
**200**
```json
[
  { "vehicleType": "cars", "id": "437", "name": "147 C/ CL" },
  { "vehicleType": "cars", "id": "438", "name": "147 Furgão (todos)" }
]
```
**404** when `brandId` does not exist (ProblemDetail).

---

## 3. Vehicle valuation history — FR-1, FR-2, FR-3, FR-6, FR-9, FR-10 (core)

```
GET /api/v1/{type}/brands/{brandId}/models/{modelId}/valuation
```
**200**
```json
{
  "vehicleType": "cars",
  "brand": "Fiat",
  "model": "147 C/ CL",
  "fipeCode": "001124-0",
  "referenceMonth": "junho de 2026",
  "currency": "BRL",
  "years": [
    { "year": 2013, "label": "2013", "fuel": "Gasolina", "price": 25000.00, "change": 2500.00, "changePercent": 11.11, "previousYear": 2011, "previousLabel": "2011" },
    { "year": 2011, "label": "2011", "fuel": "Gasolina", "price": 22500.00, "change": 2250.00, "changePercent": 11.11, "previousYear": 2010, "previousLabel": "2010" },
    { "year": 2010, "label": "2010", "fuel": "Gasolina", "price": 20250.00, "change": 2025.00, "changePercent": 11.11, "previousYear": 2009, "previousLabel": "2009" },
    { "year": 2009, "label": "2009", "fuel": "Gasolina", "price": 18225.00, "change": null, "changePercent": null, "previousYear": null, "previousLabel": null }
  ]
}
```

---

## 4. Error responses (RFC-7807) — FR-5, FR-11, FR-12

`404` (resource not found):
```json
{
  "type": "https://fipe.valuation/errors/not-found",
  "title": "Resource not found",
  "status": 404,
  "detail": "No model with id 999999 for brand 21 (cars)"
}
```

`400` (invalid vehicle type / params):
```json
{ "type": "https://fipe.valuation/errors/bad-request", "title": "Invalid request", "status": 400, "detail": "Unknown vehicle type 'planes'" }
```

`502` / `503` (FIPE upstream failure / timeout):
```json
{ "type": "https://fipe.valuation/errors/upstream", "title": "FIPE service unavailable", "status": 502, "detail": "Failed to reach FIPE" }
```

---

## 5. Conventions

- All money fields are JSON numbers serialized from `BigDecimal` scale 2 (e.g. `25000.00`).
- `change`, `changePercent`, `previousYear` are `null` for the oldest year only.
- Ordering of `years` is strictly **newest-first** (`32000`/0 km first when present).
- CORS allows `http://localhost:4200` for `GET`.
- Swagger UI served at `/swagger-ui.html`.

---

# Part B — Upstream FIPE API (consumed)

**Base URL (per fipe.online):** `https://fipe.parallelum.com.br/api/v2`. **Verified working** (all
bodies below captured from it). The base is **configurable** via `fipe.base-url`. *(The GitHub yaml
lists an alternate host `parallelum.com.br/fipe/api/v2`, which also works — not our default.)*
`{vehicleType}` is the `VehiclesType` enum: `cars | motorcycles | trucks`.

**Parameters (per fipe.online — every endpoint):**
- `reference` *(query, optional, default `278`)* — reference-table (month) id; omitted → that
  default/latest table. Documented on **all** list + detail endpoints.
- `X-Subscription-Token` *(header)* — fipe.online documents it as **required** on every endpoint.
  Empirically the host answered our probes **without** it (so it is enforced as a rate-limited
  free tier rather than hard-required). **Our client sends it whenever `fipe.subscription-token`
  is configured**, and treats it as recommended-for-production. Quotas (fipe.online): 500/day
  unauthenticated · 1,000/day with a free token · unlimited paid → caching (NFR-4) + bounded
  concurrency (NFR-2) keep us under cap; HTTP 429 → 503 (FR-12/NFR-8).

**Endpoint inventory (fipe.online order).** fipe.online groups them as **"Fipe" (#1–#7)** and
**"Busca por código Fipe" (#8–#10)** — 10 total. We consume only **#2–#5**.
`✓gh` = also present in the GitHub yaml; `✗gh` = **missing from the GitHub yaml** (incomplete cross-check).

| # | Method & path | Schema | gh? | We use it? |
|---|---------------|--------|-----|-----------|
| 1 | `GET /references` | `Reference[]` | ✓gh | Optional (month list) |
| 2 | `GET /{type}/brands` | `VehicleBrand[]` (`{code,name}`) | ✓gh | **Yes** (FR-7) |
| 3 | `GET /{type}/brands/{brandId}/models` | `VehicleModel[]` (`{code,name}`) | ✓gh | **Yes** (FR-8) |
| 4 | `GET /{type}/brands/{brandId}/models/{modelId}/years` | `VehicleYear[]` (`{code,name}`) | ✓gh | **Yes** (core) |
| 5 | `GET /{type}/brands/{brandId}/models/{modelId}/years/{yearId}` | `VehicleDetail` | ✓gh | **Yes** (core, one call per year) |
| 6 | `GET /{type}/brands/{brandId}/years` | `VehicleYear[]` | ✗gh | No (year-first nav) |
| 7 | `GET /{type}/brands/{brandId}/years/{yearId}/models` | `VehicleModel[]` | ✗gh | No (year-first nav) |
| 8 | `GET /{type}/{fipeCode}/years` | `VehicleYear[]` | ✓gh | No (FIPE-code search) |
| 9 | `GET /{type}/{fipeCode}/years/{yearId}` | `VehicleDetail` | ✓gh | No (FIPE-code search) |
| 10 | `GET /{type}/{fipeCode}/years/{yearId}/history` | `VehicleDetail` w/ `priceHistory[]` | ✓gh | No — wrong axis |

**Schemas** (fipe.online `domain.*`, corroborated by the GitHub yaml + live):
`VehicleBrand`/`VehicleModel`/`VehicleYear` = `{code, name}` · `Reference` = `{code, month}` ·
`VehicleDetail` = `{price, brand, model, modelYear:int, fuel, codeFipe, referenceMonth,
vehicleType:int, fuelAcronym, priceHistory?}` · `PriceHistory` = `{price, month, reference}`.
**Reality note:** the **price** response (#5/#9) has `referenceMonth` and **no** `priceHistory`;
the **history** response (#10) has `priceHistory[]` and **no** `referenceMonth`. Verified live.

Bodies below are **real responses captured live** from the base host above.

### Verified response bodies

> Each block shows the **exact request called** and the **real response** (arrays truncated to the
> first few items where long; truncation marked with `…`). Captured June 2026, reference table `334`.

**B.1 — `GET /references`**
```json
// GET {base}/references
[ { "code": "334", "month": "junho/2026" }, { "code": "333", "month": "maio/2026" }, … ]
```

**B.2 — `GET /{type}/brands`**
```json
// GET {base}/cars/brands
[ { "code": "1", "name": "Acura" }, { "code": "2", "name": "Agrale" }, { "code": "3", "name": "Alfa Romeo" }, … ]
// GET {base}/motorcycles/brands   → first item: { "code": "60",  "name": "ADLY" }
// GET {base}/trucks/brands        → first item: { "code": "102", "name": "AGRALE" }
```

**B.3 — `GET /{type}/brands/{brandId}/models`**
```json
// GET {base}/cars/brands/21/models   (21 = Fiat)
[ { "code": "437", "name": "147 C/ CL" }, { "code": "438", "name": "147 Furgão (todos)" }, { "code": "439", "name": "147 Pick-Up (todas)" }, … ]
// GET {base}/motorcycles/brands/60/models → first: { "code": "2576", "name": "ATV 100" }
// GET {base}/trucks/brands/102/models      → first: { "code": "5986", "name": "10000 / 10000 S  2p (diesel) (E5)" }
```

**B.4 — `GET /{type}/brands/{brandId}/models/{modelId}/years`** (code = `<modelYear>-<fuelId>`)
```json
// GET {base}/cars/brands/21/models/437/years
[ { "code": "1987-1", "name": "1987 Gasolina" }, { "code": "1986-1", "name": "1986 Gasolina" }, { "code": "1985-1", "name": "1985 Gasolina" } ]

// Multi-fuel same year — GET {base}/cars/brands/59/models/2380/years   (59 = VW, 2380 = Fusca)
[ { "code": "1996-1", "name": "1996 Gasolina" }, { "code": "1996-2", "name": "1996 Álcool" },
  { "code": "1995-1", "name": "1995 Gasolina" }, { "code": "1995-2", "name": "1995 Álcool" },
  { "code": "1994-1", "name": "1994 Gasolina" }, { "code": "1986-1", "name": "1986 Gasolina" }, { "code": "1985-1", "name": "1985 Gasolina" } ]
```

**B.5 — `GET /{type}/brands/{brandId}/models/{modelId}/years/{yearId}`** — the price object (9 fields). **This is the only endpoint that carries `vehicleType`, as an integer.**
```json
// GET {base}/cars/brands/21/models/437/years/1987-1
{ "vehicleType": 1, "price": "R$ 6.027,00", "brand": "Fiat", "model": "147 C/ CL",
  "modelYear": 1987, "fuel": "Gasolina", "codeFipe": "001124-0", "referenceMonth": "junho de 2026", "fuelAcronym": "G" }

// 0 km — GET {base}/cars/brands/21/models/11401/years/32000-5   (Argo Flex)
{ "vehicleType": 1, "price": "R$ 88.890,00", "brand": "Fiat", "model": "ARGO 1.0 6V Flex",
  "modelYear": 32000, "fuel": "Flex", "codeFipe": "001509-1", "referenceMonth": "junho de 2026", "fuelAcronym": "F" }

// motorcycle — GET {base}/motorcycles/brands/60/models/2576/years/2002-1
{ "vehicleType": 2, "price": "R$ 3.750,00", "brand": "ADLY", "model": "ATV 100",
  "modelYear": 2002, "fuel": "Gasolina", "codeFipe": "840015-6", "referenceMonth": "junho de 2026", "fuelAcronym": "G" }

// truck — GET {base}/trucks/brands/102/models/5986/years/2022-3
{ "vehicleType": 3, "price": "R$ 240.932,00", "brand": "AGRALE", "model": "10000 / 10000 S  2p (diesel) (E5)",
  "modelYear": 2022, "fuel": "Diesel", "codeFipe": "501034-9", "referenceMonth": "junho de 2026", "fuelAcronym": "D" }
```

**B.6 — `GET /{type}/brands/{brandId}/years`** (year-first nav → `VehicleYear[]`. The `32000` 0 km sentinels appear here live. ✗ missing from GitHub yaml.)
```json
// GET {base}/cars/brands/21/years
[ { "code": "32000-6", "name": "32000 Híbrido" }, { "code": "32000-5", "name": "32000 Flex" }, { "code": "32000-4", "name": "32000 Elétrico" }, … ]
```

**B.7 — `GET /{type}/brands/{brandId}/years/{yearId}/models`** (year-first nav → `VehicleModel[]`. ✗ missing from GitHub yaml.)
```json
// GET {base}/cars/brands/21/years/32000-5/models
[ { "code": "11401", "name": "ARGO 1.0 6V Flex" }, … ]
```

**B.8 — `GET /{type}/{fipeCode}/years`** (search by FIPE code → `VehicleYear[]`)
```json
// GET {base}/cars/001124-0/years
[ { "code": "1987-1", "name": "1987 Gasolina" }, { "code": "1986-1", "name": "1986 Gasolina" }, { "code": "1985-1", "name": "1985 Gasolina" } ]
```

**B.9 — `GET /{type}/{fipeCode}/years/{yearId}`** — **identical** `VehicleDetail` object as B.5
```json
// GET {base}/cars/001124-0/years/1987-1
{ "vehicleType": 1, "price": "R$ 6.027,00", "brand": "Fiat", "model": "147 C/ CL",
  "modelYear": 1987, "fuel": "Gasolina", "codeFipe": "001124-0", "referenceMonth": "junho de 2026", "fuelAcronym": "G" }
```

**B.10 — `GET /{type}/{fipeCode}/years/{yearId}/history`** — `VehicleDetail` with `priceHistory[]`: **no `referenceMonth`**
```json
// GET {base}/cars/001124-0/years/1987-1/history
{ "vehicleType": 1, "brand": "Fiat", "model": "147 C/ CL", "modelYear": 1987, "fuel": "Gasolina",
  "codeFipe": "001124-0", "fuelAcronym": "G",
  "priceHistory": [ { "price": "R$ 6.027,00", "month": "junho de 2026", "reference": "334" },
                    { "price": "R$ 6.012,00", "month": "maio de 2026",  "reference": "333" },
                    { "price": "R$ 5.931,00", "month": "abril de 2026", "reference": "332" }, … ] }
```

**B.E — Error responses (verified).** Invalid ids return **HTTP 404** with this body
(captured live via `curl` to a bad id):
```json
// GET {base}/cars/brands/99999999/models   → HTTP 404
{ "error": "failed to locate the information on fipe.org" }
```
The body is a single `error` string (no `status`/`type` fields). The client maps **by HTTP status**
(`WebClientResponseException.NotFound` → `FipeNotFoundException`); the `error` message may be
surfaced in our ProblemDetail `detail` when useful, but mapping never depends on the body.

### Why this subset
The test asks for variation **"ao longo dos anos em que o veículo foi fabricado"** (across the
**manufacture years**). That is endpoints **#4 → #5**: enumerate the model's years, fetch each
year's price in the *current* reference table, then chain the deltas. The history endpoint **#10**
is a different axis — the *same* model-year priced across *successive months* — so it is
intentionally not used. The brand-year navigations (#6/#7) and the FIPE-code-search endpoints
(#8/#9) are alternative entry points we don't need (we navigate by brand + model id).

### Client design implications
- `FipeProperties` gains `subscription-token` (optional) and a default `reference` (optional).
- `FipeClient` methods accept an optional `reference`; when present it is added as `?reference=`.
- A thin `references()` method (#1) is included so a future enhancement can let the user pick a month.
