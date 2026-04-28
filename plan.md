# Food Delivery Observability Tutorial - Technical Plan

## Architecture Overview

A polyglot microservices architecture designed to demonstrate real-world distributed system patterns while remaining simple enough to run on constrained environments.

```
┌──────────┐     ┌──────────────┐     ┌────────────────┐     ┌──────────────┐
│  Web UI  │────▶│Order Service │────▶│Restaurant Svc  │     │Delivery Svc  │
│(Node.js) │     │   (Java)     │     │    (Go)        │     │  (Python)    │
│Port 8083 │◀────│  Port 8080   │◀────│  Port 8081     │◀───▶│  Port 8082   │
└──────────┘     └──────────────┘     └────────────────┘     └──────────────┘
                         │                                            ▲
                         └────────────────────────────────────────────┘
```

## Service Architecture

### Order Service (Java Spring Boot)

**Technology Stack**:

- Java 17 (LTS for stability)
- Spring Boot 2.7 (mature, well-documented)
- Spring Web (REST APIs)
- Spring Data JPA (database abstraction)
- SQLite (embedded, no setup required)
- Gradle (dependency management)

**Why Java/Spring Boot**:

- Most enterprise systems use Java
- Automatic instrumentation well-supported
- Students likely to encounter in production
- Rich ecosystem of observability tools

**Key Responsibilities**:

- Order orchestration and workflow
- Payment processing (simulated)
- Service coordination
- Menu and pricing management
- Business logic enforcement

### Restaurant Service (Go)

**Technology Stack**:

- Go 1.21 (modern features, stable)
- Gin Web Framework (lightweight, fast)
- In-memory data structures (no external dependencies)
- Go modules (dependency management)

**Why Go**:

- Different instrumentation patterns than Java
- Growing adoption in cloud-native systems
- Excellent concurrency for queue simulation
- Simple deployment (single binary)

**Key Responsibilities**:

- Kitchen queue management
- Order preparation workflow
- Capacity management
- Real-time status updates
- Inventory tracking (simplified)

### Delivery Service (Python)

**Technology Stack**:

- Python 3.11 (modern, fast)
- FastAPI (async, automatic OpenAPI)
- Pydantic (data validation)
- In-memory driver pool
- pip/venv (dependency management)

**Why Python/FastAPI**:

- Different language ecosystem
- Async patterns for real-time tracking
- Popular in data/ML systems
- Clear, readable code for learning

**Key Responsibilities**:

- Driver assignment algorithm
- Delivery tracking
- Route optimization (simulated)
- Real-time location updates
- Delivery confirmation

### Web UI Service (Node.js)

**Technology Stack**:

- Node.js 22 LTS
- Express (simple, well-known)
- Server-sent events (real-time updates)
- Vanilla JavaScript (no build step)
- Bootstrap 5 (responsive UI)

**Why Node.js/Express**:

- Frontend developers familiar
- SSE for real-time without complexity
- No build process required
- Fast iteration during workshops

**Key Features**:

- Order placement interface
- Real-time order tracking
- Restaurant browsing
- Order history
- Admin dashboard

## Observability Strategy

### Lesson Progression

#### Lessons 000-006: Honeycomb OpenTelemetry Distribution

**Configuration Simplicity**:

```bash
# Only 2 environment variables needed
HONEYCOMB_API_KEY=your-key
SERVICE_NAME=order-service
```

**Benefits**:

- Minimal configuration overhead
- Better error messages for students
- Pre-configured for Honeycomb
- Automatic trace URL generation
- Optimal batching settings

#### Lesson 007: Standard OpenTelemetry

**Full Configuration Control**:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443
OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}"
OTEL_SERVICE_NAME=order-service
OTEL_TRACES_SAMPLER=traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1
```

**Learning Objectives**:

- Vendor neutrality
- Custom processors
- Advanced sampling
- Multi-backend export

## Data Flow Patterns

### Synchronous Flow (Order Placement)

```
Web UI → Order Service → Restaurant Service → Order Service → Web UI
                     ↓
              Delivery Service
```

### Asynchronous Patterns (Lesson 004)

```
Order Service → Queue → Background Processor → Notification Service
                           ↓
                     Dead Letter Queue
```

### Event Streaming (Real-time Updates)

```
Delivery Service → Server-Sent Events → Web UI
     ↑
GPS Simulator (30-second updates)
```

## Infrastructure Design

### GitHub Codespaces Optimization

**Resource Constraints**:

- 2 CPU cores
- 4GB RAM
- 32GB storage
- Shared kernel resources

**Optimization Strategies**:

- In-memory data stores (no external databases)
- SQLite for persistence (file-based)
- Lazy service startup
- Efficient port usage (8080-8083)
- No Docker overhead

### Development Environment

```yaml
.devcontainer/devcontainer.json:
  image: mcr.microsoft.com/devcontainers/universal:linux
  features:
    - Java 17
    - Go 1.21
    - Python 3.11
    - Node.js 22
  forwardPorts: [8080, 8081, 8082, 8083]
  postCreateCommand: setup.sh
```

### Local Development Alternative

```yaml
docker-compose.yml:
  services:
    order-service:
      build: ./order-service
      ports: ["8080:8080"]
    restaurant-service:
      build: ./restaurant-service
      ports: ["8081:8081"]
    delivery-service:
      build: ./delivery-service
      ports: ["8082:8082"]
    web-ui:
      build: ./web-ui
      ports: ["8083:8083"]
```

## Lesson Implementation Strategy

### Lesson 000: Baseline (No Observability)

- All services with basic logging only
- No correlation IDs
- No distributed tracing
- Students experience debugging pain

### Lesson 001: Cross-Service Tracing

- Add Honeycomb agents
- Automatic instrumentation only
- See service dependencies
- Trace complete workflows

### Lesson 002: Manual Spans

- Add custom instrumentation
- Measure internal operations
- Add business context
- Performance visibility

### Lesson 003: Events

- Business event tracking
- State transitions
- Error recording
- Audit trails

### Lesson 004: Async Processing

- Queue instrumentation
- Background job tracing
- Correlation across async boundaries
- Dead letter queue monitoring

### Lesson 005: Trace Links

- Connect related operations
- Refund → Original order
- Batch processing
- Compensation workflows

### Lesson 006: Performance

- Cache instrumentation
- Database query optimization
- Connection pool monitoring
- Load testing integration

### Lesson 007: Advanced OpenTelemetry

- Migrate to standard OTEL
- Custom sampling
- Multi-backend export
- Production configurations

## Performance Targets

### Service Startup

- Cold start: < 30 seconds total
- Warm reload: < 5 seconds
- Memory usage: < 500MB per service
- CPU usage: < 25% idle

### Runtime Performance

- Order placement: < 500ms p50
- Menu loading: < 100ms p50
- Status updates: < 2 seconds
- Trace visibility: < 5 seconds

### Load Testing Targets

- 10 concurrent users
- 20 requests/second peak
- 10,000 orders/hour sustained
- < 0.1% error rate

## Testing Strategy

### Performance/Chaos Tests

- Load simulation
- Failure injection

## Deployment Patterns

### Development (Codespaces)

```bash
# Each service has simple run script
cd lesson-001/order-service
./run.sh
```

### Workshop Environment

```bash
# Start all services
./scripts/start-all.sh

# Run specific lesson
./scripts/start-lesson.sh 001
```

### Production Simulation

```bash
# With monitoring
docker-compose -f docker-compose.prod.yml up

# With chaos engineering
./scripts/enable-chaos.sh
```

## Documentation Structure

### Per-Lesson Documentation

```
lesson-XXX/
├── README.md          # Learning objectives, setup
├── EXERCISES.md       # Student exercises
├── SOLUTIONS.md       # Exercise solutions
└── INSTRUCTOR.md      # Teaching notes
```

### Global Documentation

```
/
├── README.md          # Project overview
├── SETUP.md          # Environment setup
├── TROUBLESHOOTING.md # Common issues
└── ARCHITECTURE.md   # System design
```

## Success Metrics

### Technical Metrics

- Setup time: < 5 minutes
- First trace: < 2 minutes
- All services running: < 30 seconds
- Memory usage: < 2GB total

## Risk Mitigation

### Technical Risks

- **Service startup failures**: Health check endpoints
- **Resource exhaustion**: Memory limits, cleanup scripts
- **Network issues**: Retry logic

### Educational Risks

- **Too complex**: Progressive lessons, clear documentation
- **Too simple**: Advanced exercises, bonus challenges
- **Setup failures**: Automated scripts, video guides
- **Version conflicts**: Pinned dependencies, tested versions
