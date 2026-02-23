# AI_USAGE.md

## Table of Contents

1.  Overview
2.  OpenAPI Specification Prompt
3.  Unit Testing Prompt
4.  k6 Performance Testing Prompt
5.  Operational Runbook Prompt

------------------------------------------------------------------------

## Overview

This document records the AI prompts used during the design and
implementation of the Quotes API project. All AI outputs were reviewed,
validated, and adjusted by engineers before acceptance.

------------------------------------------------------------------------

## OpenAPI Specification Prompt

### Purpose

Generate a complete OpenAPI 3.0.x specification for POST /api/v1/quotes,
including idempotency, validation, and RFC7807 error modeling.

### Output Summary

-   openapi.yaml specification
-   Idempotency header definition (UUID v4)
-   Request/response schemas
-   ProblemDetails schema
-   Response codes (201, 200, 409, 400, 422, 429, 500)

### Validation Approach

-   Validated with Swagger/OpenAPI validator
-   Cross-checked with controller implementation
-   Verified against unit and integration tests

------------------------------------------------------------------------

## Unit Testing Prompt (JUnit + Mockito)

### Purpose

Generate production-quality unit tests for controller and service
layers, covering idempotency and validation scenarios.

### Output Summary

-   MockMvc controller tests
-   Service-level unit tests
-   Idempotency replay and conflict scenarios
-   Validation and error handling tests

### Validation Approach

-   Executed via Maven test phase
-   Verified branch coverage
-   Reviewed Mockito interaction verification

------------------------------------------------------------------------

## k6 Performance Testing Prompt

### Purpose

Generate a load test script to evaluate POST /api/v1/quotes under
realistic traffic conditions.

### Output Summary

-   k6 script with smoke and load scenarios
-   Thresholds for latency and error rate
-   Replay and conflict simulation logic
-   Environment variable configuration

### Validation Approach

-   Executed locally and in CI
-   Verified p95 latency and error thresholds
-   Confirmed idempotency behavior under load

------------------------------------------------------------------------

## Operational Runbook Prompt

### Purpose

Generate a production-grade operational README.md runbook for
deployment, monitoring, scaling, and incident response.

### Output Summary

-   Deployment and rollback procedures
-   Monitoring and alerting guidance
-   Idempotency operational notes
-   Disaster recovery and scaling strategy

### Validation Approach

-   Reviewed by engineering leadership
-   Cross-checked with Kubernetes manifests
-   Confirmed CI/CD alignment

