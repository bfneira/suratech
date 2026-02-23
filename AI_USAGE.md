# OpenAPI Specification Prompt

## Context
Proof of concept cloud architecture using OpenAPI 3.0, Azure DevOps, SQL and k6.

## Objective
Generate a complete and valid OpenAPI 3.0.x specification for:

POST /api/v1/quotes

## Requirements

### 1) Idempotency
- Required header: Idempotency-Key (UUID v4).
- Behavior:
  a) Same Idempotency-Key + identical body => return 200 with the original response.
  b) Same Idempotency-Key + different body => return 409 Conflict.
- Document idempotency behavior in the operation description.
- Include response codes: 201 (created), 200 (idempotent replay), 409 (conflict).

### 2) Validation
- Define strict request and response schemas.
- Include required fields, min/max constraints, string patterns, enums, and proper formats (uuid, date-time, decimal).
- Add realistic examples.
- Disallow additionalProperties where appropriate.

### 3) Error Model
- Use application/problem+json (RFC7807).
- Include responses: 400, 409, 422, 429, 500.
- Define a reusable ProblemDetails schema in components.

### 4) Additional Requirements
- Include optional X-Correlation-Id header.
- Define operationId.
- Use tags.
- Use components/schemas for all reusable models.
- Follow REST naming conventions.
- Use server URL placeholder (e.g., https://api.example.com).

## Output Rules
- Output ONLY the openapi.yaml content.
- The file must be valid OpenAPI 3.0.x YAML.
- No explanations, no commentary.
- The entire specification must be written in English.

---

# JUnit Test Generation Prompt

## Context
REST API endpoint: POST /api/v1/quotes.  
Stack: Java 17, Spring Boot, JUnit 5, Mockito.  
Idempotency is implemented with required header: Idempotency-Key (UUID v4).

Behavior:
- New request -> 201 Created.
- Same Idempotency-Key + identical body -> 200 OK (replay same response, no duplicate creation).
- Same Idempotency-Key + different body -> 409 Conflict.

## Objective
Generate production-quality unit tests for the POST /api/v1/quotes flow.

## Requirements

### 1) Test Layers
- Controller tests using MockMvc (or WebTestClient if reactive).
- Service tests using JUnit 5 + Mockito (pure unit tests).

### 2) Coverage
- 201 Created: valid request -> service called -> response contains quoteId + createdAt.
- 200 OK replay: same key + same body -> returns previously stored response; verify no new insert.
- 409 Conflict: same key + different body -> verify conflict exception and no insert.
- Validation:
    - Missing Idempotency-Key -> 400.
    - Invalid payload (e.g., missing required fields, invalid ranges) -> 400 or 422 (match your contract).
- Error mapping: ProblemDetails (application/problem+json) if used.

### 3) Conventions
- Use Arrange/Act/Assert.
- Use descriptive test names.
- Provide minimal test builders/factories for request DTOs.
- Mock dependencies (repository, idempotency store, clock/time provider if needed).
- Verify interactions with Mockito (times/never).

## Output Rules
- Output ONLY code blocks.
- Separate files with their relative paths as a line above each code block.
- All content in English.
- Do not include explanations outside code blocks.

## Assumptions
- QuotesController, QuotesService, QuotesRepository, IdempotencyStore exist.
- DTOs: CreateQuoteRequest, QuoteResponse.
- Exceptions: IdempotencyConflictException, ValidationException (or equivalents).

---

# k6 Load Testing Script Prompt

## Context
REST API endpoint: POST /api/v1/quotes.  
Performance tool: k6.  
Idempotency header: Idempotency-Key (UUID v4) is required.

## Objective
Generate a k6 load test script to performance test POST /api/v1/quotes with realistic traffic.

## Requirements

### 1) Script Behavior
- Base URL from env var: BASE_URL (default http://localhost:8080).
- Endpoint path: /api/v1/quotes.
- Content-Type: application/json.
- Generate a realistic request body (randomized values within valid ranges).
- For normal load: use a unique Idempotency-Key per request (UUID v4).
- Also include a small % of requests that intentionally reuse the same Idempotency-Key + same body to validate replay behavior (expect 200).
- Include a small % that reuse same Idempotency-Key + different body to validate conflict handling (expect 409).

### 2) Load Profile
- Provide 2 scenarios:
  a) Smoke test (short duration, low VUs)
  b) Load test (stages ramp up/down)
- Use thresholds:
    - http_req_failed < 1%
    - http_req_duration p(95) < 500ms (configurable via env var)
    - checks pass rate > 99%

### 3) Reporting
- Use checks for status codes and response schema basics.
- Log minimal debug output only when an env var DEBUG=true.

### 4) Output
- One file: performance/k6/post-quotes.js
- Include clear comments on how to run it.
- All content in English.
- Output ONLY the code block (no extra commentary).

## Notes
- If auth is not required, do not include Authorization headers.
- Keep the script deterministic enough for CI usage.

---

# Operational Runbook (README.md) Prompt

## Context
Cloud-based REST API service exposing POST /api/v1/quotes.  
Stack: Java 17, Spring Boot, SQL database.  
Deployed in Docker/Kubernetes.  
CI/CD via Azure DevOps.  
Performance testing via k6.  
Idempotency implemented via Idempotency-Key (UUID v4).

## Objective
Generate a production-grade operational Runbook as a README.md file.

## Output Requirements
- Output ONLY one file.
- The filename must be: README.md
- Output must be in a single fenced code block.
- All content must be written in English.
- Do not include explanations outside the file content.

## Mandatory Sections
1) Service Overview
2) Architecture Summary
3) Deployment & Rollback
4) Configuration & Environment Variables
5) Monitoring & Alerts
6) Idempotency Operational Guidelines
7) Incident Response Playbooks
8) Performance & Load Testing (k6)
9) Scaling Strategy
10) Disaster Recovery
11) Security Considerations

## Style
Professional, operational, concise, actionable.

## Tone
Direct, no fluff.

## Audience
DevOps engineers, SREs, senior backend engineers.