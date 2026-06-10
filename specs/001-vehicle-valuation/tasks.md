# Spec 001 — Tasks (traceable, milestone-mapped)

Each task is individually testable and linked to the requirement(s) it satisfies and the
milestone it belongs to. A task is "done" when its acceptance test is green and review clears.

Legend: FR/UI ids from [spec.md](./spec.md); rules from [data-model.md](./data-model.md);
contracts from [api-contract.md](./api-contract.md).

## M1 — Backend skeleton
- T1.1 Maven `pom.xml`: Spring Boot 3.3.x, `spring-boot-starter-webflux`, validation,
  springdoc-webflux-ui, caffeine, lombok(optional); `maven.compiler.release=17`. → NFR-5
- T1.2 `FipeValuationApplication` + `application.yml` (`fipe.base-url`, timeouts, concurrency). → NFR-3
- T1.3 `FipeProperties` (`@ConfigurationProperties("fipe")`). → NFR-3/4
- T1.4 `WebClientConfig`, `WebFluxCorsConfig` (allow :4200). → NFR-7
- **Gate:** app context loads (`SpringApplicationContextTest`).

## M2 — FIPE client
- T2.1 `VehicleType` enum (`path` + verified `fipeId` 1/2/3) + case-insensitive parse (+ 400 on unknown) + `fromFipeId`. → FR-11,14
- T2.2 wire DTOs `FipeReference`, `FipeVehiclePrice`. → data-model §1
- T2.3 `FipeClient` interface + `FipeClientImpl`: `brands`, `models`, `years`, `yearPrice`. → FR-7,8
- **Gate:** client tests against MockWebServer return mapped objects.

## M3 — Currency parser
- T3.1 `BrazilianCurrencyParser.parse(String): BigDecimal` per data-model §4. → FR-4
- **Gate:** `BrazilianCurrencyParserTest` (parametrized incl. millions, NBSP, malformed→throws).

## M4 — Valuation service (core)
- T4.1 `VehicleValuationService.valuate(type, brandId, modelId)`: fetch years → concurrent
  price fetch (bounded) → parse → sort `(modelYear asc, fuelId desc)` → chain deltas (sequential
  mixed-fuel) → reverse desc; set `previousYear`/`previousLabel`. → FR-1,2,3,9,10,13; NFR-1,2
- T4.2 0 km handling + divide-by-zero guard. → FR-6, data-model §5
- **Gate:** `VehicleValuationServiceTest` covers FR-1 (skipped-year), FR-2, FR-3, FR-6, FR-13 (Fusca multi-fuel).

## M5 — Web layer & cross-cutting
- T5.1 `FipeController` (3 endpoints) + response DTOs, each echoing `vehicleType`. → FR-7,8,9,14; contract §1-3
- T5.2 `GlobalExceptionHandler` → ProblemDetail (404/400/502/503). → FR-5,11,12
- T5.3 Caffeine cache on brands/models/years/prices. → NFR-4
- T5.4 springdoc OpenAPI annotations; verify `/v3/api-docs` matches contract. → NFR-6
- **Gate:** `FipeControllerTest` (`@WebFluxTest`) asserts JSON + error mapping.

## M6 — Backend test suite (senior-grade)
- T6.1 Service tests with MockWebServer + `StepVerifier` (all FR-1..3,6).
- T6.2 Controller tests (`@WebFluxTest` + `WebTestClient`) incl. error→ProblemDetail (FR-5,11,12).
- T6.3 `FipeValuationApplicationIT` full-context against MockWebServer (happy path + 404).
- **Gate:** `mvn test` all green; every test names its FR id.

## M7 — Frontend scaffold
- T7.1 `ng new frontend` (standalone, scss) + Angular Material + animations + pt-BR locale.
- T7.2 `proxy.conf.json` (/api → :8080); `api.config.ts`. → NFR-7
- **Gate:** `ng build` succeeds; app shell renders.

## M8 — Frontend data layer
- T8.1 `models.ts` (Brand, Model, Valuation, YearValuation) mirroring contract.
- T8.2 `FipeService` typed HttpClient calls for the 3 endpoints. → UI-1
- **Gate:** service unit spec with `HttpTestingController`.

## M9 — Valuation screen
- T9.1 `ValuationComponent`: dependent Tipo→Marca→Modelo selects, signals, loading/disabled. → UI-1,2
- T9.2 `ValuationResultComponent`: newest-first table, pt-BR currency, green/red delta,
  "em relação a {previousYear}", 0 km + oldest handling, header metadata. → UI-3,4,6
- T9.3 Error UX (friendly message, form stays usable). → UI-5
- **Gate:** component specs assert ordering, delta sign coloring, oldest row.

## M10 — End-to-end & docs
- T10.1 Root `README.md`: how to run backend + frontend, sample request.
- T10.2 Manual e2e per spec.md §5 (Carros → Fiat → model → Consultar).
- **Gate:** full flow verified; all FR/UI ids mapped to a passing test or manual check.

---

## Traceability matrix (requirement → task)

| Req | Tasks |
|-----|-------|
| FR-1 | T4.1, T6.1 |
| FR-2 | T4.1, T6.1 |
| FR-3 | T4.1, T6.1 |
| FR-4 | T3.1 |
| FR-5 | T5.2, T6.2 |
| FR-6 | T4.2, T6.1 |
| FR-7 | T2.3, T5.1, T8.2 |
| FR-8 | T2.3, T5.1, T8.2 |
| FR-9 | T5.1, T6.2 |
| FR-10 | T4.1, T6.1 |
| FR-11 | T2.1, T5.2, T6.2 |
| FR-12 | T5.2, T6.2 |
| FR-13 | T4.1, T6.1 |
| FR-14 | T2.1, T5.1, T6.2 |
| UI-1..6 | T9.1, T9.2, T9.3 |
