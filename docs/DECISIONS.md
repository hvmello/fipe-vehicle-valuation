# Architecture & Design Decisions

Short, deliberate trade-offs behind this solution. Each entry is *what we chose* and, more
importantly, *what we chose against and why* — articulated trade-offs, not silent ones.

The functional source of truth is [`specs/001-vehicle-valuation/`](../specs/001-vehicle-valuation);
milestone history is in [`progress.html`](../progress.html).

---

## 1. Money is `BigDecimal`, never floating point
Prices and variations use `BigDecimal` (scale 2, `HALF_UP`) end to end. FIPE delivers money as a
pt-BR string (`"R$ 6.027,00"`) which is parsed to `BigDecimal` and never touches `double`/`float`.
**Why:** valuation is a money domain; binary floating point can't represent decimal cents exactly.

## 2. Reactive (WebFlux + `WebClient`), bounded fan-out
One valuation needs a year list plus *N* per-year price calls. Those fan out with
`Flux.flatMap(..., maxConcurrency)` rather than a sequential blocking loop.
**Against:** a simpler blocking MVC stack. **Why:** the fan-out is I/O-bound and benefits from
non-blocking concurrency; the bound (`fipe.max-concurrency`) keeps us polite to FIPE's free tier.

## 3. Caching is a decorator, not an annotation
`CachingFipeClient implements FipeClient` (`@Primary`) wraps the raw client; the valuation service
depends only on the `FipeClient` interface and is oblivious to caching.
**Against:** Spring's `@Cacheable`. **Why:** `@Cacheable` caches the *publisher*, not the resolved
value, which is wrong for reactive types. Caffeine `AsyncCache` caches the completed value and drops
failed lookups — and keeping caching orthogonal honours OCP/DIP.

## 4. Two-tier cache, hot/cold by design (in-memory)
Two bounded caches: **catalog** (brands/models/years — stable, long TTL) and **price** (monthly).
Bounds let Caffeine's W-TinyLFU keep the hot working set and evict cold vehicles; we never prefetch
the whole catalog.
**Against:** a single uniform cache, or a distributed cache (Redis). **Why:** catalog and price have
very different change rates and access patterns; in-memory is the right scope for a single instance.
**Known limit:** the cache is per-instance and lost on restart — see decision 5.

## 5. FIPE's monthly update is a *trigger*, not just a TTL
`CurrentReferenceProvider` resolves FIPE's current reference table at startup and re-checks daily;
the price-cache key carries the reference code, and a detected rollover *actively invalidates* the
price cache (freeing the old month's RAM). TTL remains only as a safety net.
**Against:** relying on TTL expiry alone. **Why:** prices change discretely (monthly), so an
event-driven refresh is both fresher and cheaper than a short blanket TTL.
**Known limit (multi-instance):** the trigger invalidates only the local cache. Horizontal scaling
would need a shared store (Redis) or a broadcast invalidation — deliberately out of scope here.

## 6. Resilience: retry only transient failures
FIPE's free tier intermittently resets connections / returns 429. `FipeClientImpl` retries with
backoff on connection errors and 5xx/429, and **never** on 4xx (e.g. 404).
**Why:** transient blips self-heal without masking real "not found" results or hammering upstream.

## 7. Errors as RFC-7807 `ProblemDetail`
Domain exceptions (`FipeNotFoundException`, `FipeIntegrationException`) map centrally to
404 / 400 / 502. Stack traces are never leaked; expected upstream conditions never surface as 500.

## 8. Security posture: hardened transport, no app auth
CORS is restricted to the configured origin, safe methods and an explicit header allow-list (no
wildcard); baseline security headers on every response; config is `@Validated` to fail fast on
misconfiguration.
**Against:** adding authentication. **Why:** the API is public, read-only, derived from public FIPE
data — there is nothing to authorize. Actuator exposes only `health`/`info`/`metrics`.

## 9. Frontend: type-ahead for models, dropdowns for type/brand
A brand can have ~500 models. Profiling showed the backend returns in <50 ms (even cold) — the cost
was the browser rendering ~500 options. The model field is a filtering **autocomplete** rendering a
small slice; type and brand stay as dropdowns (small, fixed sets).
**Against:** pagination, or a virtualized dropdown (tried; the autocomplete is the better UX).
**Why:** users find a specific car by typing, and the panel stays light.

## 10. Spec-driven, traceable tests
Every test references the FR/NFR id it verifies, so behaviour maps back to a requirement.
**Why:** maximises reviewability and protects intent during refactors.

## 11. `npm audit` posture — don't force-break the toolchain
`npm audit` reports advisories in the **Angular build toolchain** (e.g. webpack `buildHttp`/
`HttpUriPlugin` SSRF). These are **build-time only** — they are not part of the bundle shipped to the
browser, and we don't use the affected features. The only "fix" npm offers is `npm audit fix --force`,
which pulls `@angular-devkit/build-angular` from 18 → 21 — a **breaking** framework-major upgrade.
**Decision:** stay on Angular 18 (the brief's stack) and **do not** force-upgrade for a dev-only
advisory; CI installs with `--no-audit --no-fund` to keep logs clean. Bumping Angular's major is a
deliberate, separately-tested change — not something to slip into a take-home via `--force`.

---

### Environment note — corporate TLS proxy
On a TLS-inspecting network the JVM/Node won't trust the proxy CA by default. The fix is to trust the
OS root store (`-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` for Java, `NODE_EXTRA_CA_CERTS` for
Node) — verification stays **on**, no `strict-ssl` downgrade. See the README for commands. On a normal
network none of this is needed.
