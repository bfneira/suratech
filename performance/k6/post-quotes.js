// performance/k6/post-quotes.js
//
// k6 load test for:
//   POST /api/v1/quotes
//
// Covers:
// - BASE_URL env var (default: http://localhost:8080)
// - Unique Idempotency-Key (UUID v4) per normal request
// - Small % replay: same Idempotency-Key + same body => expect 200
// - Small % conflict: same Idempotency-Key + different body => expect 409
// - Two scenarios: smoke + load (ramp stages)
// - Thresholds:
//     http_req_failed < 1%
//     http_req_duration p(95) < P95_MS (env, default 500ms)
//     checks pass rate > 99%
//
// How to run:
//
// Smoke (default):
//   k6 run -e BASE_URL="http://localhost:8080" performance/k6/post-quotes.js
//
// Load:
//   k6 run -e BASE_URL="http://localhost:8080" -e TEST_MODE="load" performance/k6/post-quotes.js
//
// Optional tuning:
//   -e P95_MS="500"
//   -e DEBUG="true"
//   -e REPLAY_PCT="2"      (default 2%)
//   -e CONFLICT_PCT="1"    (default 1%)
//   -e LOAD_STAGES="10s:5,30s:20,10s:0"  (duration:targetVUs,...)
//
// Notes:
// - No Authorization header is used (per requirements).
// - Deterministic enough for CI usage: seeded PRNG + deterministic UUID v4 generation.

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

// -------------------- config --------------------
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ENDPOINT = "/api/v1/quotes";
const DEBUG = String(__ENV.DEBUG || "false").toLowerCase() === "true";

const P95_MS = Number(__ENV.P95_MS || "500");

const REPLAY_PCT = clampInt(Number(__ENV.REPLAY_PCT || "2"), 0, 100);
const CONFLICT_PCT = clampInt(Number(__ENV.CONFLICT_PCT || "1"), 0, 100);

const TEST_MODE = String(__ENV.TEST_MODE || "smoke").toLowerCase(); // "smoke" | "load"
const LOAD_STAGES = String(__ENV.LOAD_STAGES || "10s:5,30s:20,10s:0");

// -------------------- custom metrics --------------------
const checksRate = new Rate("checks_rate");
const replayOk = new Rate("idempotency_replay_ok");
const conflictOk = new Rate("idempotency_conflict_ok");
const createdOk = new Rate("quote_created_ok");
const quotePostDuration = new Trend("quote_post_duration", true);

export const options = {
    scenarios: TEST_MODE === "load" ? buildLoadScenario() : buildSmokeScenario(),
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: [`p(95)<${P95_MS}`],
        checks_rate: ["rate>0.99"],
    },
};

// -------------------- idempotency reuse fixtures --------------------
// Stable UUIDv4 strings (match UUID v4 regex)
const REPLAY_KEY = "11111111-1111-4111-8111-111111111111";
const CONFLICT_KEY = "22222222-2222-4222-8222-222222222222";

const replayBodyObj = buildQuoteRequest({
    seed: 12345,
    documentId: "DOC-REPLAY-000001",
    customerId: "CUST-REPLAY",
});

const conflictBodyBaseObj = buildQuoteRequest({
    seed: 23456,
    documentId: "DOC-CONFLICT-000001",
    customerId: "CUST-CONFLICT",
});

export function setup() {
    if (REPLAY_PCT + CONFLICT_PCT > 100) {
        throw new Error("REPLAY_PCT + CONFLICT_PCT must be <= 100");
    }

    if (DEBUG) {
        console.log(
            JSON.stringify(
                {
                    baseUrl: BASE_URL,
                    endpoint: ENDPOINT,
                    testMode: TEST_MODE,
                    p95ms: P95_MS,
                    replayPct: REPLAY_PCT,
                    conflictPct: CONFLICT_PCT,
                    loadStages: LOAD_STAGES,
                },
                null,
                2
            )
        );
    }

    // Pre-warm: ensure the fixed keys exist in the idempotency store so replay/conflict are deterministic.
    // Expect 201 on first insert.
    prewarm("replay-prewarm", REPLAY_KEY, replayBodyObj, 201);
    prewarm("conflict-prewarm", CONFLICT_KEY, conflictBodyBaseObj, 201);

    return {
        replayBody: JSON.stringify(replayBodyObj),
        conflictBodyBase: JSON.stringify(conflictBodyBaseObj),
    };
}

export default function (data) {
    const url = `${BASE_URL}${ENDPOINT}`;

    // Arrange
    const type = pickRequestType(REPLAY_PCT, CONFLICT_PCT);

    let key;
    let body;
    let expectedStatus;

    if (type === "replay") {
        key = REPLAY_KEY;
        body = data.replayBody;
        expectedStatus = 200;
    } else if (type === "conflict") {
        key = CONFLICT_KEY;
        const obj = JSON.parse(data.conflictBodyBase);
        // Make it *valid* but *different* (documentId change + quantity bump)
        obj.documentId = `DOC-CONFLICT-${padLeft(__VU, 3)}-${padLeft(__ITER, 6)}`;
        obj.items[0].quantity = Math.min(100000, Math.max(1, (obj.items[0].quantity || 1) + 1));
        body = JSON.stringify(obj);
        expectedStatus = 409;
    } else {
        key = uuidv4Deterministic(__VU, __ITER);
        const req = buildQuoteRequest({
            seed: deterministicSeed(__VU, __ITER),
            documentId: `DOC-${padLeft(__VU, 3)}-${padLeft(__ITER, 6)}`,
            customerId: `CUST-${padLeft(__VU, 3)}`,
        });
        body = JSON.stringify(req);
        expectedStatus = 201;
    }

    const headers = {
        "Content-Type": "application/json",
        "Idempotency-Key": key,
        "X-Correlation-Id": `k6-${padLeft(__VU, 3)}-${padLeft(__ITER, 6)}`,
    };

    // Act
    const res = http.post(url, body, { headers, timeout: "5s" });

    quotePostDuration.add(res.timings.duration);

    // Assert
    const ok = check(res, {
        "status is expected": (r) => r.status === expectedStatus,
        "success: content-type is application/json": (r) => {
            if (r.status !== 200 && r.status !== 201) return true;
            return String(r.headers["Content-Type"] || "").toLowerCase().includes("application/json");
        },
        "errors: content-type is problem+json (preferred)": (r) => {
            if (r.status !== 409) return true;
            const ct = String(r.headers["Content-Type"] || "").toLowerCase();
            return ct.includes("application/problem+json") || ct.includes("application/json");
        },
        "success: response has id": (r) => {
            if (r.status !== 200 && r.status !== 201) return true;
            const j = safeJson(r);
            return !!j && typeof j.id === "string" && j.id.length >= 8;
        },
        "success: response has createdAt": (r) => {
            if (r.status !== 200 && r.status !== 201) return true;
            const j = safeJson(r);
            return !!j && typeof j.createdAt === "string" && j.createdAt.includes("T");
        },
        "replay: should not return Location header": (r) => {
            if (expectedStatus !== 200) return true;
            return r.headers["Location"] === undefined;
        },
    });

    checksRate.add(ok);

    if (type === "replay") replayOk.add(res.status === 200);
    else if (type === "conflict") conflictOk.add(res.status === 409);
    else createdOk.add(res.status === 201);

    if (DEBUG && !ok) {
        console.log(
            JSON.stringify(
                {
                    vu: __VU,
                    iter: __ITER,
                    type,
                    expectedStatus,
                    status: res.status,
                    reqHeaders: headers,
                    resHeaders: res.headers,
                    resBody: truncate(String(res.body || ""), 1000),
                },
                null,
                2
            )
        );
    }

    sleep(0.2);
}

// -------------------- scenarios --------------------
function buildSmokeScenario() {
    return {
        smoke: {
            executor: "constant-vus",
            vus: 2,
            duration: "10s",
            gracefulStop: "5s",
            tags: { test_type: "smoke" },
        },
    };
}

function buildLoadScenario() {
    return {
        load: {
            executor: "ramping-vus",
            startVUs: 0,
            stages: parseStages(LOAD_STAGES),
            gracefulRampDown: "10s",
            gracefulStop: "10s",
            tags: { test_type: "load" },
        },
    };
}

// -------------------- request generation --------------------
function buildQuoteRequest({ seed, documentId, customerId }) {
    const rng = mulberry32(seed);

    const currency = pick(rng, ["CLP", "USD", "EUR"]);
    const itemsCount = randInt(rng, 1, 3);

    const items = [];
    for (let i = 0; i < itemsCount; i++) {
        const quantity = randInt(rng, 1, 10);
        const unitPrice = round2(randFloat(rng, 100, 50000));
        const taxRate = pick(rng, [0, 0.19]);

        items.push({
            sku: `SKU-${padLeft(randInt(rng, 1, 9999), 4)}`,
            name: `Item ${i + 1}`,
            quantity,
            unitPrice,
            taxRate,
        });
    }

    const daysAhead = randInt(rng, 1, 30);
    const expiresAt = isoNowPlusDays(daysAhead);

    return {
        documentId,
        customer: {
            id: customerId,
            email: `customer.${customerId.toLowerCase()}@example.com`,
        },
        currency,
        items,
        expiresAt,
        metadata: {
            channel: pick(rng, ["web", "mobile", "partner"]),
            campaign: `cmp-${padLeft(randInt(rng, 1, 999), 3)}`,
        },
    };
}

// -------------------- idempotency distribution --------------------
function pickRequestType(replayPct, conflictPct) {
    const x = deterministicMod(__VU, __ITER, 100);
    if (x < replayPct) return "replay";
    if (x < replayPct + conflictPct) return "conflict";
    return "normal";
}

function prewarm(label, key, bodyObj, expectedStatus) {
    const url = `${BASE_URL}${ENDPOINT}`;
    const headers = {
        "Content-Type": "application/json",
        "Idempotency-Key": key,
        "X-Correlation-Id": `k6-setup-${label}`,
    };

    const res = http.post(url, JSON.stringify(bodyObj), { headers, timeout: "10s" });
    const ok = check(res, { [`setup ${label}: status ${expectedStatus}`]: (r) => r.status === expectedStatus });

    checksRate.add(ok);

    if (DEBUG) {
        console.log(
            JSON.stringify(
                { setup: label, status: res.status, body: truncate(String(res.body || ""), 500) },
                null,
                2
            )
        );
    }
}

// -------------------- utilities --------------------
function safeJson(res) {
    try {
        return res.json();
    } catch (_) {
        return null;
    }
}

function parseStages(spec) {
    const parts = spec.split(",").map((s) => s.trim()).filter(Boolean);
    if (parts.length === 0) throw new Error("LOAD_STAGES must not be empty");

    return parts.map((p) => {
        const [duration, targetStr] = p.split(":").map((s) => s.trim());
        if (!duration || targetStr === undefined) throw new Error(`Invalid stage: ${p}`);
        const target = Number(targetStr);
        if (!Number.isFinite(target) || target < 0) throw new Error(`Invalid target VUs in stage: ${p}`);
        return { duration, target };
    });
}

function deterministicSeed(vu, iter) {
    return (vu * 1_000_003 + iter * 97) >>> 0;
}

function deterministicMod(vu, iter, mod) {
    const seed = deterministicSeed(vu, iter);
    const mixed = (seed * 1664525 + 1013904223) >>> 0;
    return mixed % mod;
}

function uuidv4Deterministic(vu, iter) {
    const rng = mulberry32(deterministicSeed(vu, iter));
    const hex = () => randInt(rng, 0, 15).toString(16);

    let out = "";
    for (let i = 0; i < 36; i++) {
        if (i === 8 || i === 13 || i === 18 || i === 23) {
            out += "-";
            continue;
        }
        if (i === 14) {
            out += "4";
            continue;
        }
        if (i === 19) {
            out += pick(rng, ["8", "9", "a", "b"]);
            continue;
        }
        out += hex();
    }
    return out;
}

function mulberry32(a) {
    return function () {
        let t = (a += 0x6d2b79f5);
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

function randInt(rng, min, max) {
    return Math.floor(rng() * (max - min + 1)) + min;
}

function randFloat(rng, min, max) {
    return rng() * (max - min) + min;
}

function pick(rng, arr) {
    return arr[randInt(rng, 0, arr.length - 1)];
}

function round2(x) {
    return Math.round(x * 100) / 100;
}

function isoNowPlusDays(days) {
    const d = new Date(Date.now() + days * 24 * 60 * 60 * 1000);
    return d.toISOString();
}

function padLeft(n, width) {
    const s = String(n);
    return s.length >= width ? s : "0".repeat(width - s.length) + s;
}

function truncate(s, maxLen) {
    return s.length <= maxLen ? s : s.substring(0, maxLen) + "...";
}

function clampInt(v, min, max) {
    if (!Number.isFinite(v)) return min;
    return Math.min(max, Math.max(min, Math.floor(v)));
}