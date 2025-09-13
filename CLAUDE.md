# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an observability tutorial repository demonstrating OpenTelemetry instrumentation with Honeycomb. It contains progressive examples (001-006) showing different aspects of distributed tracing using Java Spring Boot applications and Go services.

## Environment Setup

**Critical**: The `HONEYCOMB_API_KEY` must be set before running any examples:
- For local development: Export as environment variable or set in `.env` file
- For GitHub Codespaces: Update the `.env` file in the root directory
- All example scripts automatically load from `.env` if present

## Build and Run Commands

### Basic Examples (001-005)
Each example follows the same pattern:
```bash
cd 00X-[example-name]
./bnd.sh  # Builds with Gradle and runs with appropriate agent
```

### Cross-Service Example (006)
Two services that need to be run separately:
```bash
cd 006-cross-service
./bnd.sh      # Runs Java service on port 8086
./bnd-go.sh   # Runs Go service (separate terminal)
```

### Testing Services
All Java services expose a `/joke` endpoint:
```bash
curl http://localhost:8086/joke
```

## Architecture Overview

### OpenTelemetry Java Agent Configuration
Examples 002-006 use the OpenTelemetry Java agent (`lib/opentelemetry-javaagent.jar`) for automatic instrumentation. The agent is configured via environment variables in the `bnd.sh` scripts:

- `OTEL_EXPORTER_OTLP_ENDPOINT`: Set to Honeycomb's API endpoint
- `OTEL_EXPORTER_OTLP_HEADERS`: Contains the Honeycomb team API key
- `OTEL_SERVICE_NAME`: Identifies the service in traces
- `OTEL_METRICS_EXPORTER`: Set to "none" (focusing on traces only)

### Service Communication Pattern
The 006-cross-service example demonstrates distributed tracing across languages:
- Java service (Spring Boot) handles joke requests
- Go service uses Honeycomb Beeline for instrumentation
- Both services send traces to the same Honeycomb dataset for correlation

### Progressive Learning Structure
1. **001-basic**: Simple Spring Boot app without instrumentation
2. **002-spans**: Introduces OpenTelemetry Java agent
3. **003-async**: Demonstrates async operations tracing
4. **004-events**: Shows custom events in traces
5. **005-links**: Trace linking concepts
6. **006-cross-service**: Multi-service distributed tracing

## Key Implementation Details

- All Java services use Spring Boot 2.6.7 with Gradle build system
- Random delays (0-250ms) are added to simulate realistic latency
- The joke endpoints contain hardcoded jokes array for consistent testing
- Go service uses Honeycomb Beeline SDK for native instrumentation
- Agent JAR files are shared in `lib/` directory to avoid duplication