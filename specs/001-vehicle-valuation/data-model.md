# Spec 001 — Data Model & Calculation Rules

This is the **single source of truth** for the valuation computation. The service and the
tests both implement exactly what is written here.

> **All shapes below were verified against the live FIPE v2 API** (every endpoint called and
> the raw JSON inspected field-by-field). Nothing here is assumed.
>
> **Primary source:** the FIPE docs at `https://fipe.online/docs/api/fipe` (`domain.*` schemas:
> `Reference`, `VehicleBrand`, `VehicleModel`, `VehicleYear`, `VehicleDetail`, `PriceHistory`) —
> all field names/types match our captures. The docs model the detail + history as one
> `VehicleDetail` whose `priceHistory` is optional; **in practice the price response (#5/#9)
> carries `referenceMonth` and no `priceHistory`, while the history response (#10) carries
> `priceHistory[]` and no `referenceMonth`.** We split these into `FipeVehiclePrice` vs. the
> history shape accordingly. (The GitHub yaml — secondary cross-check only — agrees on fields but
> is missing endpoints #6/#7, so we defer to the primary docs + live data.)

## 1. FIPE wire models (upstream, consumed) — verified

```jsonc
// GET /{type}/brands  AND  /{type}/brands/{brandId}/models   → array; objects have EXACTLY 2 fields
{ "code": "21", "name": "Fiat" }                              // code:string, name:string

// GET /{type}/brands/{brandId}/models/{modelId}/years         → array; objects have EXACTLY 2 fields
{ "code": "1987-1",  "name": "1987 Gasolina" }                // code = "<modelYear>-<fuelId>"
{ "code": "32000-5", "name": "32000 Flex" }                   // 0 km entry (verified live)

// GET /{type}/brands/{brandId}/models/{modelId}/years/{yearId}  → object with EXACTLY 9 fields (verified)
{
  "vehicleType": 1,                // number  (verified 1=cars,2=motorcycles,3=trucks) — used for integrity check (§Type integrity)
  "price": "R$ 6.027,00",          // string  pt-BR currency — PARSE to BigDecimal
  "brand": "Fiat",                 // string
  "model": "147 C/ CL",            // string
  "modelYear": 1987,               // number  — 32000 == "0 km" (verified: Argo Flex returns 32000)
  "fuel": "Gasolina",              // string
  "codeFipe": "001124-0",          // string
  "referenceMonth": "junho de 2026", // string
  "fuelAcronym": "G"               // string  — we IGNORE
}
```

> Verified extras: a 0 km lookup (`.../models/11401/years/32000-5`) returns `"modelYear": 32000`,
> `"price": "R$ 88.890,00"`. The fipeCode-search detail endpoint
> (`/{type}/{fipeCode}/years/{yearId}`) returns the **identical** 9-field object.

```jsonc
// GET /references   (reference tables / months)
{ "code": "334", "month": "junho/2026" }
```

```jsonc
// GET /{type}/{fipeCode}/years/{yearId}/history  → object; NOTE: no referenceMonth; has priceHistory[]
{
  "vehicleType": 1, "brand": "Fiat", "model": "147 C/ CL", "modelYear": 1987,
  "fuel": "Gasolina", "codeFipe": "001124-0", "fuelAcronym": "G",
  "priceHistory": [
    { "price": "R$ 6.027,00", "month": "junho de 2026", "reference": "334" },
    { "price": "R$ 6.012,00", "month": "maio de 2026",  "reference": "333" }
  ]
}
```

Internal wire DTOs (Jackson, `@JsonIgnoreProperties(ignoreUnknown=true)`, package-private to client):
- `FipeReference { String code; String name; }`  ← brands, models, years (all share this shape)
- `FipeVehiclePrice { int vehicleType; String price; String brand; String model; int modelYear; String fuel; String codeFipe; String referenceMonth; String fuelAcronym; }` ← we use price + modelYear + fuel + codeFipe + referenceMonth + brand + model
- `FipeReferenceTable { String code; String month; }`   // e.g. {"334","junho/2026"}
- (history endpoint #10 has its own `priceHistory[]` shape — **not consumed**, documented for completeness)

**Common upstream parameters** (see api-contract Part B): every FIPE call accepts an optional
`reference` (query — reference table/month id; omitted = latest) and an optional
`X-Subscription-Token` (header). The client sends them when configured.

**Upstream errors (verified):** invalid ids return HTTP **404** with body
`{ "error": "failed to locate the information on fipe.org" }` (single `error` string). The client
maps by **status**: `WebClientResponseException.NotFound` → `FipeNotFoundException`; 5xx/timeout →
`FipeIntegrationException`. The `error` text may be surfaced in `detail`; mapping never depends on it.

## 2. Domain types

```
enum VehicleType {
  CARS("cars", 1), MOTORCYCLES("motorcycles", 2), TRUCKS("trucks", 3);
  String path;   // URL segment for our API and FIPE
  int fipeId;    // integer FIPE reports in the price object's "vehicleType" field
}
```
- **Verified live mapping:** `1=cars` (Fiat), `2=motorcycles` (ADLY ATV), `3=trucks` (AGRALE).
- Parsed case-insensitively from the path variable; unknown → 400 (FR-11).
- `fromFipeId(int)` resolves the enum from the price object's integer, used for the integrity
  check in §"Type integrity" below.

## 3. API response models (downstream, produced)

`ValuationResponse`:
```jsonc
{
  "vehicleType": "cars",
  "brand": "Fiat",
  "model": "147 C/ CL",
  "fipeCode": "001124-0",
  "referenceMonth": "junho de 2026",
  "currency": "BRL",
  "years": [ YearValuationResponse, ... ]   // newest first
}
```

`YearValuationResponse`:
```jsonc
{
  "year": 2013,                 // int; 32000 for 0 km
  "label": "2013",              // "0 km" when year == 32000
  "fuel": "Gasolina",
  "price": 25000.00,            // BigDecimal, scale 2
  "change": 2500.00,            // BigDecimal scale 2, or null for oldest
  "changePercent": 11.00,       // BigDecimal scale 2, or null for oldest
  "previousYear": 2011,         // int, or null for oldest
  "previousLabel": "2011"       // label of the compared entry ("1996 Álcool" for cross-fuel), null for oldest
}
```

`BrandResponse { String vehicleType; String id; String name; }` — `id` from FIPE `code`; `vehicleType` echoed from the request (FIPE list endpoints omit it). (FR-14)
`ModelResponse { String vehicleType; String id; String name; }` — `vehicleType` echoed from the request. (FR-14)

### Type integrity (defensive)
FIPE's price object carries an integer `vehicleType`. The service asserts it equals the
requested `VehicleType.fipeId`; a mismatch is logged as an anomaly (does not fail the request,
since the path is authoritative). This is why the field is mapped rather than ignored.

## 4. Currency parsing rule (FR-4)

Input examples → output `BigDecimal` (scale 2):
- `"R$ 6.027,00"` → `6027.00`
- `"R$ 1.234.567,89"` → `1234567.89`
- `"R$ 0,00"` → `0.00`
- `"R$ 18.225,00"` → `18225.00`
- May contain a non-breaking space (` `) after `R$`.

Algorithm (`BrazilianCurrencyParser.parse`):
1. Reject `null`/blank → `IllegalArgumentException`.
2. Strip everything except digits, `.`, `,` (removes `R$`, spaces, NBSP).
3. Remove thousands separators `.`; replace decimal `,` with `.`.
4. `new BigDecimal(normalized).setScale(2, HALF_UP)`.
5. Anything non-numeric after normalization → `IllegalArgumentException` (FR-4 negative case).

## 5. Calculation rules (FR-1, FR-2, FR-3, FR-10)

Given the parsed list of `(modelYear, fuelId, fuel, price)` entries for a model:

1. **Sort ascending** by `modelYear`, then by **`fuelId` descending** as the tiebreaker
   (so after the final reversal, the newest year leads and, within a year, the lowest fuelId —
   Gasolina — appears first; see §6).
2. Walk the sorted list. For index `i`:
   - `i == 0` (oldest): `change = null`, `changePercent = null`, `previousYear = null`.
   - `i > 0`: let `prev = entries[i-1]`.
     - `change = price[i] − price[i-1]` → scale 2, HALF_UP.
     - `changePercent = (change / price[i-1]) × 100` → scale 2, HALF_UP.
       - If `price[i-1] == 0` → `changePercent = null` (avoid divide-by-zero; `change` still set).
     - `previousYear = prev.modelYear`.
3. **Reverse** the list so output is **newest-first** (FR-1 ordering).

The "previous" year is always the **previous element in the year-sorted sequence**, which is
why a gap (missing 2012) makes 2013 compare to 2011 automatically.

## 6. Ordering & ties — sequential mixed-fuel (FR-6, FR-13)

**Decision (confirmed):** chaining is **sequential by year, mixed fuel** — every entry compares
to the immediately preceding entry in the year-sorted sequence, *regardless of fuel*.

- `fuelId` is the integer suffix of the year code (`"1996-2"` → `2`). Verified mapping:
  `1=Gasolina, 2=Álcool, 3=Diesel, 4=Elétrico, 5=Flex, 6=Híbrido`.
- `modelYear == 32000` ("0 km") sorts as the **largest** year → newest after reversal; `label = "0 km"`.
- **Same-year multi-fuel (verified live — VW Fusca model 2380):** `1996-1 Gasolina` and
  `1996-2 Álcool` coexist. With sort `(modelYear asc, fuelId desc)` the ascending sequence is
  `… 1995A, 1995G, 1996A, 1996G`, and the newest-first output is `1996G, 1996A, 1995G, 1995A`.
  Each entry chains off the previous element, so **`1996 Gasolina` is compared to `1996 Álcool`**
  (a same-year, cross-fuel delta) and **`previousYear` may equal `year`**.
- This cross-fuel comparison is a **deliberate, documented simplification** (chosen over per-fuel
  series): it keeps one flat, deterministic series and exactly matches the test example for the
  common single-fuel model. The single-fuel case (the example, and most models) is unaffected.
- To make the cross-fuel/same-year case legible in the UI, each year entry also carries
  `previousLabel` (e.g. `"1996 Álcool"`) so the screen can render "em relação a {previousLabel}"
  instead of a bare year when they differ.

## 7. Worked example (must match FR-1 exactly)

Input prices (ascending): 2009=18225.00, 2010=20250.00, 2011=22500.00, 2013=25000.00

| Year | Price | change | % | previousYear |
|------|-------|--------|----|--------------|
| 2009 | 18225.00 | null | null | null |
| 2010 | 20250.00 | 2025.00 | 11.11 | 2009 |
| 2011 | 22500.00 | 2250.00 | 11.11 | 2010 |
| 2013 | 25000.00 | 2500.00 | 11.11 | 2011 |

> Note: the test's prose rounds 11.11% to "11%". We keep 2 decimals (`11.11`) for precision;
> the UI may display rounded. `change` values match the example exactly (2025/2250/2500).
> Output order is reversed: 2013, 2011, 2010, 2009.

## 8. Error model (FR-5, FR-11, FR-12)

RFC-7807 `ProblemDetail` for all errors:
- `FipeNotFoundException` → 404 (`type=.../not-found`, `detail` names the missing resource).
- Invalid `VehicleType` / bad params → 400.
- `FipeIntegrationException` (FIPE 5xx, timeout, parse failure) → 502 (or 503 on timeout).
- FIPE **429** (daily quota exhausted, see NFR-8) → 503 (`detail` mentions rate limiting).
