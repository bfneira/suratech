# README.md — Operational Runbook (POST /api/v1/quotes Service)

## 1) Service Overview
**Service**: Quotes API  
**Primary capability**: Create quotes via `POST /api/v1/quotes` with strict validation and idempotency.  
**Core guarantees**:
- **Idempotency** via required `Idempotency-Key` (UUID v4).
- **No duplicate creation** on retries.
- **Operational visibility** via health endpoints and correlation IDs.

**Key endpoints**
- `POST /api/v1/quotes` — create quote (idempotent)
- `GET /actuator/health` — overall health
- `GET /actuator/health/liveness` — liveness probe
- `GET /actuator/health/readiness` — readiness probe

**Success criteria (SLO-oriented)**
- Availability: ≥ 99.9% (define per environment)
- Latency: p95 < 500ms for `POST /api/v1/quotes` under expected load (tune per tier)
- Error rate: < 1% (4xx excluded unless contract violations are significant)

---

## 2) Architecture Summary
**Runtime**: Java 21 + Spring Boot  
**Data store**: SQL database (quotes + idempotency records)  
**Deployment**: Docker image deployed to Kubernetes  
**CI/CD**: Azure DevOps pipeline builds, tests, builds image, runs integration smoke tests  
**Performance testing**: k6 scripts (see Section 8)

**Request flow (high level)**
1. Client sends `POST /api/v1/quotes` with JSON body and `Idempotency-Key`.
2. API validates request payload (bean validation).
3. Idempotency layer:
    - Existing key + same body: replay stored result.
    - Existing key + different body: reject with 409.
    - New key: execute quote creation and persist idempotency record with TTL.
4. Persist quote data.
5. Return response (201 created or 200 replay).

**Operational dependencies**
- SQL database (required)
- Kubernetes cluster networking/ingress (required)
- Container runtime (required)
- Optional messaging/outbox components (if enabled in your environment)

---

## 3) Deployment & Rollback

### Deployment (Kubernetes)
**Pre-flight**
- Confirm database connectivity and credentials availability (Secret mounted / env injected).
- Confirm image is present in registry and tag is correct.
- Confirm migrations strategy (if applicable): pre-run job or startup migration.

**Deploy**
- Apply manifests/Helm chart:
    - Deployment/StatefulSet
    - Service
    - Ingress/HTTPRoute
    - ConfigMap/Secret
    - HPA (if enabled)
- Wait for rollout:
    - Pods Ready
    - Readiness probe passes
    - Service endpoints stable
- Validate:
    - `GET /actuator/health/readiness` == `UP`
    - Smoke request to `POST /api/v1/quotes` with a new `Idempotency-Key` => 201
    - Repeat same key+body => 200
    - Same key different body => 409

### Rollback
**When**
- Elevated 5xx error rate
- Readiness failing beyond grace period
- DB connection storms / pool exhaustion
- Severe regression confirmed

**How**
- Kubernetes:
    - `kubectl rollout undo deployment/<name>`
    - Verify previous ReplicaSet is healthy and ready
- Helm:
    - `helm rollback <release> <revision>`
- After rollback:
    - Re-run the same validation checks as deployment

**Rollback considerations**
- If DB schema changed incompatibly, rollback may require DB rollback or forward-fix.
- Ensure idempotency table changes are backward compatible (strong recommendation).

---

## 4) Configuration & Environment Variables

### Application
| Variable | Required | Default | Description |
|---|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | No | (none) | Spring profile selector (e.g., `ci`, `local`, `prod`). |
| `SPRING_DATASOURCE_URL` | Yes | - | JDBC URL to SQL database. |
| `SPRING_DATASOURCE_USERNAME` | Yes | - | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | - | Database password. |
| `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` | No | `false` | Enables readiness/liveness probe endpoints. |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | No | - | Expose actuator endpoints (recommend: `health,info`). |
| `SERVER_PORT` | No | `8080` | HTTP listen port. |

### Idempotency
| Variable | Required | Default | Description |
|---|---:|---|---|
| `IDEMPOTENCY_TTL_SECONDS` (or `idempotency.ttlSeconds`) | Yes | - | TTL for idempotency records. Recommended: `86400` (24h). |

> Standardize the property name across environments. If using Spring relaxed binding, prefer one canonical name and map it consistently.

### Observability / Logging
| Variable | Required | Default | Description |
|---|---:|---|---|
| `LOG_LEVEL` (implementation-specific) | No | - | Set log level (avoid DEBUG in prod). |

### Optional integrations (if present)
| Variable | Required | Default | Description |
|---|---:|---|---|
| `APP_MESSAGING_SERVICEBUS_ENABLED` | No | `false` | Enable/disable external messaging publisher. |
| `APP_OUTBOX_ENABLED` | No | `true` | Enable/disable outbox processing (recommended toggle for incident mitigation). |

### Header conventions
- `Idempotency-Key` (required): UUID v4
- `X-Correlation-Id` (optional): client-provided correlation value; service should echo or generate and return.

---

## 5) Monitoring & Alerts

### Metrics to monitor
**Service health**
- Readiness/liveness status
- Pod restarts
- CrashLoopBackOff counts

**HTTP**
- Request rate (RPS)
- Latency percentiles (p50, p90, p95, p99) for `POST /api/v1/quotes`
- Error rate:
    - 5xx (service issues)
    - 4xx (client issues; track 409 and 422 separately)

**Database**
- Connection pool usage (active/idle/wait)
- Slow queries / query timeouts
- Deadlocks / lock waits (idempotency and quote inserts can contend under load)
- DB CPU/IO utilization

**Idempotency**
- Replay rate (200 vs 201 ratio)
- Conflict rate (409)
- Idempotency table growth rate
- Cleanup job effectiveness (expired records removed)

### Alert recommendations
**Paging (high severity)**
- Readiness failing > 5 minutes (or sustained across multiple pods)
- 5xx rate above threshold (e.g., > 1% for 5 min)
- p95 latency above target (e.g., > 500ms for 10 min)
- Database connection acquisition timeouts / pool exhaustion

**Ticket (medium severity)**
- Rising 409 conflict rate (possible client misuse)
- Rising 422 validation failures (client regression)
- Idempotency table growth abnormal (cleanup broken)

### Log correlation
- Ensure logs include:
    - `X-Correlation-Id` (or generated correlation id)
    - `Idempotency-Key` (careful: treat as operational identifier, not secret)
    - Quote ID on successful create/replay
- Do not log PII beyond necessity.

---

## 6) Idempotency Operational Guidelines

### Behavior (contract)
- New key + valid body => **201 Created**
- Same key + identical body (within TTL) => **200 OK** (replay original response)
- Same key + different body (within TTL) => **409 Conflict**

### Storage model (SQL-friendly)
Recommended fields:
- `idempotency_key` (PK, UUID or BINARY(16))
- `request_hash` (SHA-256, CHAR(64) hex or BINARY(32))
- `quote_id` (UUID or BINARY(16))
- `created_at`, `expires_at`

Recommended indices:
- Primary key on `idempotency_key`
- Secondary index on `expires_at` for cleanup

### TTL guidance
- Default: **24 hours** (`86400` seconds)
- Increase TTL if clients may retry late (batch jobs, async workflows)
- Decrease TTL if volume is very high and replay window is shorter
- Ensure cleanup runs regularly to avoid unbounded growth.

### Operational checks
- Validate replay works:
    - Send same key+body twice; second must be 200 and same quote id.
- Validate conflict works:
    - Send same key with a modified body; must return 409.

### Client integration rules (enforce via API gateway docs)
- Clients must generate UUID v4 keys per logical operation.
- Clients must reuse the same key only when retrying the same operation.
- Clients must treat 409 as a non-retryable contract error (investigate client bug).

---

## 7) Incident Response Playbooks

### Common incident signals
- Readiness failing / pods not becoming ready
- 5xx spike
- Latency spike (p95/p99)
- DB connection pool saturation
- Conflict spike (409)
- Validation spike (422)

### Playbook A — Service not ready (readiness failing)
1. Check current rollout status (new deploy?):
    - Inspect recent deployment events.
2. Inspect logs of failing pods:
    - Look for datasource errors, missing env vars, external dependency init failures.
3. Verify database connectivity:
    - DNS, network policy, credentials, DB availability.
4. Mitigation:
    - Roll back to last known good release.
    - Temporarily disable optional integrations (outbox/messaging) if they block startup.
5. Verify recovery:
    - readiness returns `UP`
    - smoke request passes

### Playbook B — 5xx spike
1. Scope:
    - Is it all endpoints or `POST /api/v1/quotes` only?
2. Check DB:
    - connection pool, timeouts, slow queries, deadlocks.
3. Check saturation:
    - CPU throttling, memory pressure, GC, thread pool exhaustion.
4. Mitigation:
    - Scale out replicas (if CPU-bound)
    - Reduce traffic (rate limit at gateway)
    - Disable optional background jobs if contributing load
5. Follow-up:
    - Capture request samples with correlation IDs
    - Identify failing code path and add regression tests

### Playbook C — Latency spike (p95 > target)
1. Identify bottleneck:
    - DB query times (idempotency lookup + quote inserts)
    - Lock contention in idempotency table
2. Check resource limits:
    - CPU throttling, JVM heap pressure
3. Mitigation:
    - Scale out pods
    - Tune DB pool size (carefully; do not overwhelm DB)
    - Add index / optimize queries if missing
4. Postmortem:
    - Add k6 regression baseline and alert thresholds.

### Playbook D — Conflict (409) spike
1. Confirm it’s real:
    - Sample logs: same `Idempotency-Key` used with different body.
2. Likely causes:
    - Client bug generating key per request but mixing payloads
    - Cache/proxy replaying header incorrectly
3. Mitigation:
    - Communicate with client owners
    - If necessary, temporarily reject traffic patterns at gateway (rate limit offending client)
4. Follow-up:
    - Improve error payload clarity and client documentation.

### Playbook E — Idempotency table growth / cleanup failure
1. Verify TTL config is present and correct.
2. Check cleanup job (if implemented) and execution logs.
3. Mitigation:
    - Run manual cleanup: delete where `expires_at < now()`
    - Add index on `expires_at` if missing
4. Follow-up:
    - Add scheduled cleanup with safe batch deletes.

---

## 8) Performance & Load Testing (k6)

### Purpose
- Validate latency SLOs and error rate under realistic traffic.
- Validate idempotency behavior under load:
    - replay 200
    - conflict 409

### Scripts
- Primary script: `performance/k6/post-quotes.js`

### How to run
Smoke:

bash k6 run -e BASE_URL="[http://localhost:8080](http://localhost:8080)" performance/k6/post-quotes.js

bash k6 run -e BASE_URL="[http://localhost:8080](http://localhost:8080)" -e TEST_MODE="load" performance/k6/post-quotes.js

bash k6 run
-e BASE_URL="[http://localhost:8080](http://localhost:8080)"
-e TEST_MODE="load"
-e P95_MS="500"
-e REPLAY_PCT="2"
-e CONFLICT_PCT="1"
-e DEBUG="false"
-e LOAD_STAGES="10s:5,30s:20,10s:0"
performance/k6/post-quotes.js

### Pass/Fail (CI gate)
- `http_req_failed < 1%`
- `http_req_duration p(95) < P95_MS` (default 500ms)
- `checks pass rate > 99%`

### Operational notes
- Run load tests against a dedicated environment sized similarly to production.
- Ensure DB has realistic capacity; otherwise tests measure DB saturation, not service.
- Always pre-warm fixed idempotency keys for deterministic replay/conflict assertions.

---

## 9) Scaling Strategy

### Horizontal scaling (Kubernetes)
Use HPA based on:
- CPU utilization (baseline)
- request rate (if metrics available)
- latency (advanced; requires custom metrics)

Guidelines:
- If CPU-bound: add replicas.
- If DB-bound: scaling pods may worsen DB overload; prefer query/index optimization and/or DB scaling.

### Database scaling
- Ensure proper indexing on idempotency key and expiration.
- Consider read replicas for read-heavy patterns (replay lookups) if consistent with correctness.
- Monitor lock contention; consider transaction isolation impacts.

### Tuning levers
- Connection pool sizing: set conservative per-pod limits to protect DB.
- JVM: set memory limits and GC tuning appropriate for container runtime.
- Backpressure: implement rate limiting at ingress/gateway.

---

## 10) Disaster Recovery

### Backups
- Enable automated DB backups with defined RPO/RTO.
- Validate restore procedures regularly (game days).

### Restore procedure (high level)
1. Declare incident and stop write traffic if required.
2. Restore DB to last known good point in time (PITR if available).
3. Redeploy service (if needed) pointing to restored DB.
4. Validate:
    - health endpoints are `UP`
    - `POST /api/v1/quotes` works
    - idempotency semantics remain correct

### RPO/RTO (set explicitly)
- RPO: X minutes/hours (depends on backup frequency)
- RTO: Y minutes/hours (depends on restore automation)

---

## 11) Security Considerations

### Transport security
- Enforce TLS at ingress.
- Prefer mTLS between internal services if applicable.

### Authentication/Authorization
- If enabled, use JWT/OAuth2 at gateway or app layer.
- Ensure least privilege for service identity (DB credentials, secrets access).

### Secrets management
- Store secrets in Kubernetes Secrets or a managed secret store.
- Rotate DB passwords/keys regularly.
- Never log credentials or tokens.

### Data handling
- Avoid logging request bodies (may contain sensitive data).
- Mask/omit PII in logs by default.
- Apply database encryption at rest where possible.

### Dependency hygiene
- Pin dependency versions; monitor CVEs.
- Rebuild images regularly and scan artifacts in CI.

---