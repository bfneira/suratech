All prompts were executed directly in IntelliJ IDEA using the integrated AI assistant, leveraging the full project context (source files, configuration, dependencies, and actual code structure).
This allowed the generated solutions to be aligned with the existing architecture, stack

# 1.- OpenAPI 3.0 Prompt

```Context:
Proof of concept cloud architecture using OpenAPI 3.0.

Objective:
Generate a complete and valid OpenAPI 3.0.x specification for:

POST /api/v1/quotes

Requirements:

1) Idempotency:
  - Required header: Idempotency-Key (UUID v4).
  - Behavior:
    a) Same Idempotency-Key + identical body => return 200 with the original response.
    b) Same Idempotency-Key + different body => return 409 Conflict.
  - Document idempotency behavior in the operation description.
  - Include response codes: 201 (created), 200 (idempotent replay), 409 (conflict).

2) Validation:
  - Define strict request and response schemas.
  - Include required fields, min/max constraints, string patterns, enums, and proper formats (uuid, date-time, decimal).
  - Add realistic examples.
  - Disallow additionalProperties where appropriate.

3) Error Model:
  - Use application/problem+json (RFC7807).
  - Include responses: 400, 409, 422, 429, 500.
  - Define a reusable ProblemDetails schema in components.

4) Additional Requirements:
  - Include optional X-Correlation-Id header.
  - Define operationId.
  - Use tags.
  - Use components/schemas for all reusable models.
  - Follow REST naming conventions.
  - Use server URL placeholder http://localhost:8080/

Output Rules:
- Output ONLY the openapi.yaml content.
- The file must be valid OpenAPI 3.0.x YAML.
- No explanations, no commentary.
- The entire specification must be written in English.
```

Output:


![Generated Image](https://i.postimg.cc/dtPGzvMd/result1.png)


# 2.- JUnit Test Generation Prompt

```## Context
REST API endpoint: POST /api/v1/quotes.  
Stack: Java 21, Spring Boot, JUnit 5, Mockito.  
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
```

Output:


![Generated Image](https://i.postimg.cc/1zhxFfbg/result2.png)
