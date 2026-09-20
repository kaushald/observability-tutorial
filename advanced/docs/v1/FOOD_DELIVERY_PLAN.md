# Food Delivery System - Observability Tutorial Implementation Plan

## Project Overview

A modern food delivery system designed to teach observability concepts through practical, relatable scenarios. This replaces the current joke API with a multi-service architecture that demonstrates real-world distributed system patterns, failures, and performance challenges.

### Key Design Principles
- **Codespaces-First**: All services must run efficiently on free GitHub Codespaces
- **Progressive Complexity**: Each lesson builds upon the previous
- **Realistic Scenarios**: Use actual food delivery workflows that students can relate to
- **Clear Error Patterns**: Distinguish between business logic and system errors
- **Interactive Learning**: Hands-on debugging exercises with observable problems

## System Architecture

### Core Services

#### 1. Order Service (Java Spring Boot)
**Port**: 8080  
**Responsibilities**:
- Order management (create, update, cancel)
- Menu catalog
- Pricing calculations
- Payment processing (simulated)
- Order status tracking

**Technology Stack**:
- Java 17
- Spring Boot 2.7
- SQLite for persistence
- Honeycomb OpenTelemetry Java Agent (Lessons 001-006)
- Standard OpenTelemetry Java Agent (Lesson 007)

**Key Endpoints**:
```
POST   /orders                 - Create new order
GET    /orders/{id}            - Get order details
PUT    /orders/{id}/status     - Update order status
GET    /orders/{id}/track      - Real-time tracking
POST   /orders/{id}/cancel     - Cancel order
GET    /menu                   - Get restaurant menu
GET    /restaurants            - List available restaurants
```

#### 2. Restaurant Service (Go)
**Port**: 8081  
**Responsibilities**:
- Kitchen queue management
- Order acceptance/rejection
- Preparation time estimation
- Inventory management (simplified)
- Kitchen capacity tracking

**Technology Stack**:
- Go 1.21
- Gin Web Framework
- In-memory queue
- Honeycomb OpenTelemetry SDK (Lessons 001-006)
- Standard OpenTelemetry Go SDK (Lesson 007)

**Key Endpoints**:
```
POST   /orders/accept          - Accept incoming order
POST   /orders/reject          - Reject order (with reason)
GET    /orders/queue           - View kitchen queue
PUT    /orders/{id}/ready      - Mark order ready for pickup
GET    /capacity               - Current kitchen capacity
GET    /menu/availability      - Check item availability
```

#### 3. Delivery Service (Python)
**Port**: 8082  
**Responsibilities**:
- Driver pool management
- Delivery assignment algorithm
- Route optimization (simulated)
- Delivery tracking
- Driver status management

**Technology Stack**:
- Python 3.11
- FastAPI
- In-memory driver pool
- Honeycomb OpenTelemetry SDK (Lessons 001-006)
- Standard OpenTelemetry Python SDK (Lesson 007)

**Key Endpoints**:
```
POST   /deliveries/assign      - Assign driver to order
GET    /deliveries/{id}/track  - Track delivery progress
PUT    /deliveries/{id}/status - Update delivery status
GET    /drivers/available      - List available drivers
POST   /drivers/{id}/location  - Update driver location
```

#### 4. Web UI Service (Node.js/Express)
**Port**: 8083  
**Responsibilities**:
- Serve static frontend files
- WebSocket for real-time updates
- Server-sent events for order tracking
- Simple dashboard

**Technology Stack**:
- Node.js 18
- Express
- Vanilla JavaScript frontend
- Bootstrap 5 for styling
- Server-sent events
- Honeycomb OpenTelemetry SDK (Lessons 001-006)
- Standard OpenTelemetry Node.js SDK (Lesson 007)

**Frontend Features**:
- Order placement form
- Real-time order tracking
- Restaurant browser
- Order history
- Simple admin dashboard

## OpenTelemetry Configuration Strategy

### Lessons 001-006: Honeycomb OpenTelemetry Distribution
Using Honeycomb's distribution for simplified setup and better student experience:

**Benefits**:
- Minimal configuration (only HONEYCOMB_API_KEY and SERVICE_NAME required)
- Pre-configured for Honeycomb's endpoints
- Better error messages for debugging
- Automatic trace URL generation
- Optimal batching and retry settings

**Simple Environment Setup**:
```bash
# Only two environment variables needed!
export HONEYCOMB_API_KEY="your-api-key"
export SERVICE_NAME="order-service"
```

### Lesson 007: Standard OpenTelemetry
Transitioning to vendor-neutral OpenTelemetry for advanced concepts:

**Learning Objectives**:
- Understand vendor-neutral instrumentation
- Configure custom exporters
- Implement advanced sampling strategies
- Add custom processors and extensions
- Learn when to use standard vs vendor-specific distributions

**Full Configuration Required**:
```bash
export OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io:443"
export OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}"
export OTEL_SERVICE_NAME="order-service"
export OTEL_METRICS_EXPORTER="none"
export OTEL_TRACES_SAMPLER="traceidratio"
export OTEL_TRACES_SAMPLER_ARG="0.1"
```

This progression teaches students both ease-of-use (Honeycomb distribution) and flexibility (standard OTEL).

## Lesson Structure

### 000-baseline: Introduction Without Observability
**Learning Objectives**:
- Understand the food delivery system architecture
- Experience debugging challenges without observability
- Identify pain points that observability will solve
- Establish baseline for comparison with instrumented versions

**What Students Will Do**:
1. Start all four services (Order, Restaurant, Delivery, Web UI)
2. Place orders through the web interface
3. Try to debug when things go wrong
4. Experience the frustration of distributed system debugging

**Implementation Details**:
- All services run WITHOUT any OpenTelemetry instrumentation
- Basic logging only (console.log, print statements)
- No distributed tracing
- No correlation IDs
- No metrics collection

**Debugging Challenges to Demonstrate**:
```
Scenario 1: "My order is stuck"
- Student places order
- Order doesn't complete
- Which service is the problem?
- Is it the restaurant service? Delivery service? Database?
- Only option: Check logs in 4 different terminals

Scenario 2: "Some orders are slow"
- Intermittent slowness
- No way to identify which orders are affected
- Can't trace the slow component
- Logs show success but not timing

Scenario 3: "Orders failing during lunch rush"
- Works fine with low load
- Fails under high load
- Which service is overwhelmed?
- How to identify the bottleneck?
```

**Run Scripts (No Instrumentation)**:
```bash
# Order Service - run.sh
#!/bin/bash
java -jar build/libs/order-service.jar

# Restaurant Service - run.sh
#!/bin/bash
./restaurant-service

# Delivery Service - run.sh
#!/bin/bash
python main.py

# Web UI - run.sh
#!/bin/bash
node server.js
```

**Key Teaching Points**:
- Show correlation is impossible without trace IDs
- Demonstrate log sprawl across services
- Highlight timing invisibility
- Show how errors don't indicate root cause
- Demonstrate that logs alone aren't enough

**Exercise**: "Find the Bug"
Give students a broken scenario:
1. 20% of orders fail randomly
2. Only tools available: service logs
3. Task: Identify which service causes failures
4. Twist: It's actually a timeout between services (invisible in logs)

This lesson sets up the "why" of observability before teaching the "how".

### 001-cross-service: Distributed Tracing Fundamentals
**Learning Objectives**:
- Understand distributed tracing across services
- Identify service dependencies
- Observe actual system failures vs business logic
- Trace complete order flow

**Implementation Details**:
```
Customer Order Flow:
1. Customer places order (Web UI → Order Service)
2. Validate payment (Order Service internal)
3. Check restaurant availability (Order Service → Restaurant Service)
4. Restaurant accepts order (Restaurant Service → Order Service)
5. Assign driver (Order Service → Delivery Service)
6. Track delivery (Web UI → Delivery Service via SSE)
```

**System Errors to Implement**:
- Network timeouts between services
- Database connection failures
- Service unavailability (503 errors)
- Rate limiting (429 errors)
- Invalid data formats (400 errors)

**Business Scenarios** (not errors):
- Restaurant at capacity
- No drivers available
- Item out of stock
- Customer cancellation

### 002-manual-spans: Detailed Performance Visibility
**Learning Objectives**:
- Add custom spans for business logic
- Measure specific operations
- Identify performance bottlenecks
- Add span attributes for debugging

**Code Examples**:
```java
// Order Service - Payment processing with detailed spans
@Traced
public PaymentResult processPayment(Order order) {
    Span span = tracer.spanBuilder("payment.process")
        .setAttribute("order.id", order.getId())
        .setAttribute("payment.amount", order.getTotal())
        .startSpan();
    
    try (Scope scope = span.makeCurrent()) {
        // Validate payment method
        Span validationSpan = tracer.spanBuilder("payment.validate")
            .startSpan();
        validatePaymentMethod(order.getPaymentMethod());
        validationSpan.end();
        
        // Process with payment gateway
        Span gatewaySpan = tracer.spanBuilder("payment.gateway")
            .setAttribute("gateway.name", "stripe")
            .startSpan();
        PaymentResult result = paymentGateway.charge(order);
        gatewaySpan.setAttribute("payment.success", result.isSuccess());
        gatewaySpan.end();
        
        return result;
    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        throw e;
    } finally {
        span.end();
    }
}
```

```go
// Restaurant Service - Kitchen operations
func prepareOrder(ctx context.Context, order Order) error {
    ctx, span := tracer.Start(ctx, "kitchen.prepare",
        trace.WithAttributes(
            attribute.Int("order.items", len(order.Items)),
            attribute.String("order.priority", order.Priority),
        ))
    defer span.End()
    
    // Track queue wait time
    queueCtx, queueSpan := tracer.Start(ctx, "kitchen.queue.wait")
    queuePosition := kitchen.AddToQueue(order)
    queueSpan.SetAttributes(attribute.Int("queue.position", queuePosition))
    
    // Wait for turn
    <-kitchen.WaitForTurn(order.ID)
    queueSpan.End()
    
    // Prepare each item
    for _, item := range order.Items {
        itemCtx, itemSpan := tracer.Start(ctx, "kitchen.prepare.item",
            trace.WithAttributes(
                attribute.String("item.name", item.Name),
                attribute.Int("item.quantity", item.Quantity),
            ))
        
        time.Sleep(item.PrepTime) // Simulate cooking
        itemSpan.End()
    }
    
    return nil
}
```

### 003-events: Business and System Event Tracking
**Learning Objectives**:
- Add events for important moments
- Distinguish business events from errors
- Track state transitions
- Create audit trails

**Event Categories**:

**Business Events**:
```python
# Delivery Service
span.add_event("driver_assigned", {
    "driver_id": driver.id,
    "driver_name": driver.name,
    "distance_to_restaurant_km": 2.3,
    "estimated_pickup_time": "2024-01-10T12:30:00Z"
})

span.add_event("arrived_at_restaurant", {
    "wait_time_seconds": 120,
    "other_orders_waiting": 3
})

span.add_event("order_delivered", {
    "delivery_time_minutes": 28,
    "customer_available": True,
    "tip_amount": 5.00
})
```

**System Events**:
```java
// Circuit breaker events
span.addEvent("circuit_breaker_open", 
    Attributes.of(
        AttributeKey.stringKey("service"), "restaurant-service",
        AttributeKey.longKey("failure_count"), 5,
        AttributeKey.longKey("timeout_ms"), 5000
    ));

// Retry events
span.addEvent("retry_attempt",
    Attributes.of(
        AttributeKey.stringKey("operation"), "assign_driver",
        AttributeKey.intKey("attempt"), 2,
        AttributeKey.longKey("backoff_ms"), 1000
    ));

// Fallback events
span.addEvent("fallback_activated",
    Attributes.of(
        AttributeKey.stringKey("reason"), "primary_service_down",
        AttributeKey.stringKey("fallback_type"), "cached_menu"
    ));
```

### 004-async: Asynchronous Processing Patterns
**Learning Objectives**:
- Trace async operations
- Handle message queues
- Implement correlation IDs
- Debug async failures

**Implementation Components**:

**Order Queue Processing**:
```python
# Async order processor with tracing
async def process_order_queue():
    while True:
        # Start a new trace for queue processor run
        with tracer.start_as_current_span("queue.processor.run") as span:
            try:
                # Receive message with timeout
                message = await queue.receive(timeout=30)
                
                if message:
                    # Link to original trace
                    links = [Link(message.trace_context)]
                    with tracer.start_as_current_span(
                        "queue.message.process", 
                        links=links
                    ) as msg_span:
                        msg_span.set_attribute("message.id", message.id)
                        msg_span.set_attribute("message.type", message.type)
                        
                        # Process based on message type
                        if message.type == "order.placed":
                            await handle_order_placed(message.payload)
                        elif message.type == "payment.completed":
                            await handle_payment_completed(message.payload)
                        
                        # Acknowledge message
                        await queue.ack(message)
                        msg_span.add_event("message_acknowledged")
                        
            except QueueTimeout:
                span.add_event("queue_empty")
            except ProcessingError as e:
                span.record_exception(e)
                # Send to dead letter queue
                await dlq.send(message, reason=str(e))
                span.add_event("sent_to_dlq", {"error": str(e)})
```

**Background Tasks**:
- Inventory sync every 5 minutes
- Driver location updates every 30 seconds
- Order status notifications
- Analytics aggregation

### 005-links: Tracing Related Operations
**Learning Objectives**:
- Link related but separate traces
- Track operation dependencies
- Debug complex workflows
- Implement compensation patterns

**Link Scenarios**:

```java
// Link refund to original order
public void processRefund(String orderId, String reason) {
    // Get original order span context
    SpanContext originalContext = getOrderSpanContext(orderId);
    
    Span refundSpan = tracer.spanBuilder("refund.process")
        .addLink(originalContext, 
            Attributes.of(
                AttributeKey.stringKey("link.type"), "refund",
                AttributeKey.stringKey("refund.reason"), reason
            ))
        .startSpan();
    
    try (Scope scope = refundSpan.makeCurrent()) {
        // Refund logic
        refundPayment(orderId);
        notifyCustomer(orderId, "Refund processed");
        
        // Create compensation order if needed
        if (reason.equals("delivery_failed")) {
            Span reorderSpan = tracer.spanBuilder("order.compensate")
                .addLink(originalContext)
                .addLink(refundSpan.getSpanContext())
                .startSpan();
            
            createCompensationOrder(orderId);
            reorderSpan.end();
        }
    } finally {
        refundSpan.end();
    }
}
```

**Complex Workflows**:
- Order → Delivery Failed → Refund → Reorder
- Batch orders from same restaurant
- Customer support ticket → Order investigation
- Scheduled orders → Execution traces

### 006-performance: Optimization & Caching
**Learning Objectives**:
- Identify performance bottlenecks using traces
- Implement caching strategies
- Optimize database queries
- Handle high load scenarios
- Use trace data to guide optimization decisions

**Note**: Still using Honeycomb OpenTelemetry Distribution for simplicity

**Performance Patterns to Implement**:

1. **Why Standard OpenTelemetry**:
   - Vendor neutrality (can switch between Honeycomb, Jaeger, Datadog, etc.)
   - More configuration control
   - Custom span processors
   - Advanced sampling strategies
   - Multi-backend export (send to multiple observability platforms)

2. **Migration Steps**:
   ```bash
   # From Honeycomb Distribution (Lessons 001-005):
   java -javaagent:honeycomb-opentelemetry-javaagent.jar \
     -Dhoneycomb.api.key=${HONEYCOMB_API_KEY} \
     -Dservice.name=order-service \
     -jar app.jar
   
   # To Standard OpenTelemetry (Lesson 006):
   java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.exporter.otlp.endpoint=https://api.honeycomb.io:443 \
     -Dotel.exporter.otlp.headers="x-honeycomb-team=${HONEYCOMB_API_KEY}" \
     -Dotel.service.name=order-service \
     -Dotel.traces.sampler=traceidratio \
     -Dotel.traces.sampler.arg=0.1 \
     -jar app.jar
   ```

3. **Advanced Configuration Examples**:
   ```python
   # Custom sampling based on endpoint
   class CustomSampler(Sampler):
       def should_sample(self, context, trace_id, name, kind, attributes, links):
           # Always sample errors
           if attributes.get("http.status_code", 0) >= 500:
               return SamplingResult(Decision.RECORD_AND_SAMPLE)
           # Sample 10% of health checks
           if attributes.get("http.target") == "/health":
               return SamplingResult(Decision.DROP if random.random() > 0.1 else Decision.RECORD_AND_SAMPLE)
           # Sample 100% of orders
           if "order" in attributes.get("http.target", ""):
               return SamplingResult(Decision.RECORD_AND_SAMPLE)
           # Default 50% sampling
           return SamplingResult(Decision.RECORD_AND_SAMPLE if random.random() > 0.5 else Decision.DROP)
   ```

**Performance Issues to Create and Fix**:

**1. N+1 Query Problem**:
```java
// BEFORE - N+1 queries
public List<OrderDetail> getOrderDetails(List<String> orderIds) {
    List<OrderDetail> details = new ArrayList<>();
    for (String orderId : orderIds) {
        Order order = orderRepo.findById(orderId);  // Query 1
        for (OrderItem item : order.getItems()) {
            MenuItem menu = menuRepo.findById(item.getMenuId());  // Query N
            details.add(new OrderDetail(order, menu));
        }
    }
    return details;
}

// AFTER - Optimized with JOIN
public List<OrderDetail> getOrderDetailsOptimized(List<String> orderIds) {
    return orderRepo.findOrderDetailsWithMenu(orderIds);  // Single query with JOIN
}
```

**2. Cache Implementation**:
```python
# Redis cache with monitoring
class MenuCache:
    def __init__(self, redis_client):
        self.redis = redis_client
        self.metrics = CacheMetrics()
    
    async def get_menu(self, restaurant_id: str) -> Menu:
        span = trace.get_current_span()
        cache_key = f"menu:{restaurant_id}"
        
        # Try cache first
        cached = await self.redis.get(cache_key)
        if cached:
            span.set_attribute("cache.hit", True)
            self.metrics.record_hit()
            return json.loads(cached)
        
        # Cache miss - fetch from database
        span.set_attribute("cache.hit", False)
        self.metrics.record_miss()
        
        # Prevent cache stampede with distributed lock
        async with self.redis.lock(f"lock:{cache_key}", timeout=5):
            # Double-check cache after acquiring lock
            cached = await self.redis.get(cache_key)
            if cached:
                return json.loads(cached)
            
            # Fetch from database
            menu = await self.fetch_from_db(restaurant_id)
            
            # Store in cache with jitter to prevent synchronized expiration
            ttl = 3600 + random.randint(-300, 300)
            await self.redis.setex(cache_key, ttl, json.dumps(menu))
            
            return menu
```

**3. Connection Pool Monitoring**:
```go
// Monitor and alert on pool exhaustion
func monitorDBPool(pool *sql.DB) {
    ticker := time.NewTicker(10 * time.Second)
    for range ticker.C {
        stats := pool.Stats()
        
        ctx, span := tracer.Start(context.Background(), "db.pool.stats")
        span.SetAttributes(
            attribute.Int("pool.open", stats.OpenConnections),
            attribute.Int("pool.in_use", stats.InUse),
            attribute.Int("pool.idle", stats.Idle),
            attribute.Int64("pool.wait_count", stats.WaitCount),
            attribute.Int64("pool.wait_duration_ms", stats.WaitDuration.Milliseconds()),
        )
        
        // Alert if pool is exhausted
        if stats.OpenConnections == stats.MaxOpenConnections {
            span.AddEvent("pool_exhausted", trace.WithAttributes(
                attribute.Int("waiting_requests", int(stats.WaitCount)),
            ))
        }
        
        span.End()
    }
}
```

### 007-advanced-otel: Migration to Standard OpenTelemetry
**Learning Objectives**:
- Migrate from Honeycomb distribution to standard OpenTelemetry
- Understand vendor-neutral vs vendor-specific instrumentation
- Configure advanced tracing features (sampling, processors)
- Implement multi-backend export
- Create custom span processors
- Learn when to use standard vs vendor-specific distributions

**Migration Rationale**:
After mastering observability concepts with Honeycomb's simplified distribution, students learn the vendor-neutral approach that works with any backend.

**Key Differences to Highlight**:

| Aspect | Honeycomb Distribution | Standard OpenTelemetry |
|--------|------------------------|----------------------|
| Config Complexity | 2 env vars | 6+ env vars |
| Vendor Lock-in | Honeycomb-specific | Any OTLP backend |
| Custom Processors | Limited | Full control |
| Sampling Strategies | Basic | Advanced |
| Multi-backend | No | Yes |
| Error Messages | User-friendly | Technical |

**Implementation Changes**:

1. **Java Migration**:
```bash
# From (Lessons 001-006):
java -javaagent:honeycomb-opentelemetry-javaagent.jar \
  -Dhoneycomb.api.key=${HONEYCOMB_API_KEY} \
  -Dservice.name=order-service

# To (Lesson 007):
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.exporter.otlp.endpoint=https://api.honeycomb.io:443 \
  -Dotel.exporter.otlp.headers="x-honeycomb-team=${HONEYCOMB_API_KEY}" \
  -Dotel.service.name=order-service \
  -Dotel.traces.sampler=traceidratio \
  -Dotel.traces.sampler.arg=0.1
```

2. **Advanced Sampling Configuration**:
```python
from opentelemetry.sdk.trace.sampling import Sampler, SamplingResult, Decision

class SmartSampler(Sampler):
    """Sample based on endpoint and error status"""
    
    def should_sample(self, context, trace_id, name, kind, attributes, links):
        # Always sample errors
        if attributes.get("error", False):
            return SamplingResult(Decision.RECORD_AND_SAMPLE)
        
        # 1% sampling for health checks
        if "/health" in attributes.get("http.target", ""):
            return SamplingResult(
                Decision.RECORD_AND_SAMPLE if random.random() < 0.01 
                else Decision.DROP
            )
        
        # 100% sampling for payment endpoints
        if "/payment" in attributes.get("http.target", ""):
            return SamplingResult(Decision.RECORD_AND_SAMPLE)
        
        # 10% default sampling
        return SamplingResult(
            Decision.RECORD_AND_SAMPLE if random.random() < 0.1 
            else Decision.DROP
        )
```

3. **Multi-Backend Export**:
```go
// Send traces to both Honeycomb and local Jaeger
func setupMultiExport() {
    // Honeycomb exporter
    honeycombExporter, _ := otlptracehttp.New(ctx,
        otlptracehttp.WithEndpoint("api.honeycomb.io:443"),
        otlptracehttp.WithHeaders(map[string]string{
            "x-honeycomb-team": os.Getenv("HONEYCOMB_API_KEY"),
        }),
    )
    
    // Local Jaeger exporter for debugging
    jaegerExporter, _ := jaeger.New(
        jaeger.WithCollectorEndpoint(
            jaeger.WithEndpoint("http://localhost:14268/api/traces"),
        ),
    )
    
    // Combine exporters
    tp := trace.NewTracerProvider(
        trace.WithBatcher(honeycombExporter),
        trace.WithBatcher(jaegerExporter),
        trace.WithSampler(trace.TraceIDRatioBased(0.1)),
    )
}
```

4. **Custom Span Processor**:
```javascript
class SecurityProcessor extends SpanProcessor {
    onStart(span) {
        // Add security context to all spans
        span.setAttributes({
            'user.authenticated': isAuthenticated(),
            'request.ip': getClientIP(),
            'security.tier': getSecurityTier()
        });
    }
    
    onEnd(span) {
        // Audit sensitive operations
        if (span.attributes['operation.sensitive']) {
            auditLog.record(span);
        }
    }
}

// Register processor
tracerProvider.addSpanProcessor(new SecurityProcessor());
```

**Student Exercises**:

1. **"Multi-Environment Setup"**
   - Configure local development to send to Jaeger
   - Configure staging to send to Honeycomb with 10% sampling
   - Configure production to send to Honeycomb with 1% sampling

2. **"Cost Optimization"**
   - Implement head-based sampling to reduce costs
   - Create custom sampler that samples based on user tier
   - Measure cost reduction while maintaining visibility

3. **"Vendor Migration"**
   - Switch from Honeycomb to Datadog (configuration only)
   - Add Prometheus metrics alongside traces
   - Implement trace-metric correlation

**Key Takeaways**:
- Honeycomb distribution is perfect for getting started quickly
- Standard OpenTelemetry provides flexibility for complex scenarios
- Choose based on your needs: simplicity vs. control
- Both approaches send the same trace data to Honeycomb

## Load Testing Scenarios

### Scenario 1: Lunch Rush Pattern
```bash
#!/bin/bash
# load-test-lunch.sh
echo "Starting lunch rush simulation..."

# Gradual ramp-up (11:00 AM - 11:30 AM)
for i in {1..30}; do
    rate=$((10 + i * 5))  # 10 to 160 RPS
    echo "Ramping up: ${rate} requests/sec"
    hey -n 1000 -c ${rate} -m POST \
        -H "Content-Type: application/json" \
        -d @sample-order.json \
        http://localhost:8080/orders &
    sleep 60
done

# Peak load (11:30 AM - 1:00 PM)
echo "Peak lunch hour: 200 requests/sec"
hey -n 100000 -c 200 -q 200 -m POST \
    -H "Content-Type: application/json" \
    -d @sample-order.json \
    http://localhost:8080/orders &

# Gradual decline (1:00 PM - 2:00 PM)
for i in {30..1}; do
    rate=$((10 + i * 5))
    echo "Ramping down: ${rate} requests/sec"
    hey -n 1000 -c ${rate} -m POST \
        -H "Content-Type: application/json" \
        -d @sample-order.json \
        http://localhost:8080/orders &
    sleep 60
done
```

### Scenario 2: Flash Sale Spike
```python
# load-test-spike.py
import asyncio
import aiohttp
import random
from datetime import datetime

async def place_order(session, order_data):
    """Place a single order"""
    async with session.post('http://localhost:8080/orders', json=order_data) as response:
        return response.status, await response.json()

async def spike_test():
    """Simulate sudden traffic spike"""
    connector = aiohttp.TCPConnector(limit=1000)
    async with aiohttp.ClientSession(connector=connector) as session:
        # Normal traffic (50 RPS)
        print("Normal traffic: 50 RPS")
        for _ in range(300):  # 5 minutes
            tasks = [place_order(session, generate_order()) for _ in range(50)]
            await asyncio.gather(*tasks)
            await asyncio.sleep(1)
        
        # Sudden spike (1000 RPS)
        print(f"SPIKE at {datetime.now()}: 1000 RPS")
        for _ in range(60):  # 1 minute spike
            tasks = [place_order(session, generate_order()) for _ in range(1000)]
            await asyncio.gather(*tasks)
            await asyncio.sleep(1)
        
        # Return to normal
        print("Returning to normal: 50 RPS")
        for _ in range(300):
            tasks = [place_order(session, generate_order()) for _ in range(50)]
            await asyncio.gather(*tasks)
            await asyncio.sleep(1)

if __name__ == "__main__":
    asyncio.run(spike_test())
```

## Failure Injection Framework

### Chaos Engineering Middleware

```python
# chaos_middleware.py
import random
import asyncio
from fastapi import HTTPException
from opentelemetry import trace

class ChaosMiddleware:
    """Inject controlled failures for testing"""
    
    def __init__(self, app, failure_config):
        self.app = app
        self.config = failure_config
        
    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        
        path = scope["path"]
        span = trace.get_current_span()
        
        # Check if chaos is enabled for this endpoint
        if self.should_inject_failure(path):
            failure_type = self.select_failure_type()
            span.add_event("chaos_injection", {
                "type": failure_type,
                "endpoint": path,
                "controlled": True
            })
            
            if failure_type == "latency":
                delay = random.uniform(1, 5)
                span.set_attribute("chaos.latency_sec", delay)
                await asyncio.sleep(delay)
                
            elif failure_type == "error_500":
                await self.send_error(send, 500, "Chaos: Internal Server Error")
                return
                
            elif failure_type == "timeout":
                await asyncio.sleep(30)  # Cause client timeout
                
            elif failure_type == "partial_failure":
                # Randomly fail some requests
                if random.random() < 0.3:
                    await self.send_error(send, 503, "Chaos: Service Unavailable")
                    return
        
        await self.app(scope, receive, send)
```

### Database Failure Simulation

```java
// DatabaseChaos.java
@Component
public class DatabaseChaos {
    private final Tracer tracer;
    private final Random random = new Random();
    
    @Value("${chaos.database.enabled:false}")
    private boolean chaosEnabled;
    
    @Value("${chaos.database.failure-rate:0.1}")
    private double failureRate;
    
    public void maybeInjectFailure(String operation) throws SQLException {
        if (!chaosEnabled || random.nextDouble() > failureRate) {
            return;
        }
        
        Span span = tracer.currentSpan();
        String failureType = selectFailureType();
        
        span.addEvent("database_chaos", 
            Attributes.of(
                AttributeKey.stringKey("operation"), operation,
                AttributeKey.stringKey("failure_type"), failureType
            ));
        
        switch (failureType) {
            case "connection_timeout":
                try {
                    Thread.sleep(30000);  // 30 second timeout
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new SQLException("Connection timeout (chaos)");
                
            case "lock_timeout":
                throw new SQLException("Lock wait timeout exceeded (chaos)");
                
            case "connection_pool_exhausted":
                throw new SQLException("No connections available (chaos)");
                
            case "transaction_rollback":
                throw new SQLException("Transaction rolled back (chaos)");
        }
    }
}
```

## Student Exercises

### Exercise 0: Debugging Blind (Lesson 000)
**Scenario**: "Orders are failing but we don't know why"

**Available Tools**: 
- Console logs from 4 services
- No trace IDs
- No timing information
- No correlation between services

**Task**: Find which service is causing failures

**Learning Outcome**: Experience the pain of debugging without observability

### Exercise 1: Mystery of the Slow Orders (Lesson 001)
**Scenario**: "During lunch rush, some orders take 30+ seconds while others complete in 2 seconds"

**Investigation Steps**:
1. Use distributed tracing to identify slow orders
2. Compare trace waterfalls between fast and slow orders
3. Identify which service is causing delays
4. Find the pattern (e.g., specific restaurant, payment type)

**Solution**: Restaurant service has synchronous menu refresh that blocks orders

### Exercise 2: The Case of the Missing Notifications (Lesson 004)
**Scenario**: "Customers complain they don't receive delivery notifications"

**Investigation Steps**:
1. Trace notification flow from order completion
2. Check async queue processing
3. Identify where notifications are lost
4. Review dead letter queue

**Solution**: Queue consumer crashes on malformed phone numbers

### Exercise 3: Payment Spike Investigation (Lesson 007)
**Scenario**: "Payment processing times spike every hour"

**Investigation Steps**:
1. Analyze payment service metrics
2. Correlate with cache TTL
3. Identify cache stampede pattern
4. Implement fix with jittered expiration

**Solution**: All cache entries expire simultaneously causing database overload

## Run Scripts Examples

### Lesson 000: No Instrumentation

**Java (Order Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env
# Just run the service - no instrumentation
java -jar build/libs/order-service.jar
```

**Python (Delivery Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env
# Plain Python - no OpenTelemetry
python main.py
```

**Go (Restaurant Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env
# Direct execution - no tracing
./restaurant-service
```

**Node.js (Web UI) - run.sh**:
```bash
#!/bin/bash
source ../../.env
# Basic Node.js - no instrumentation
node server.js
```

### Lessons 001-006: Using Honeycomb Distribution

**Java (Order Service) - run.sh**:
```bash
#!/bin/bash
# Load environment variables
source ../../.env

# Simple configuration with Honeycomb distribution
java -javaagent:../../lib/honeycomb-opentelemetry-javaagent.jar \
  -Dhoneycomb.api.key=${HONEYCOMB_API_KEY} \
  -Dservice.name=order-service \
  -jar build/libs/order-service.jar
```

**Python (Delivery Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Install Honeycomb distribution
pip install -q honeycombio-opentelemetry

# Run with minimal configuration
HONEYCOMB_API_KEY=${HONEYCOMB_API_KEY} \
SERVICE_NAME=delivery-service \
python main.py
```

**Go (Restaurant Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Honeycomb SDK provides simpler setup
HONEYCOMB_API_KEY=${HONEYCOMB_API_KEY} \
SERVICE_NAME=restaurant-service \
./restaurant-service
```

**Node.js (Web UI) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Use Honeycomb's Node.js distribution
HONEYCOMB_API_KEY=${HONEYCOMB_API_KEY} \
SERVICE_NAME=web-ui \
node --require @honeycombio/opentelemetry-node/register server.js
```

### Lesson 007: Using Standard OpenTelemetry

**Java (Order Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Full configuration with standard OpenTelemetry
java -javaagent:../../lib/opentelemetry-javaagent.jar \
  -Dotel.exporter.otlp.endpoint=https://api.honeycomb.io:443 \
  -Dotel.exporter.otlp.headers="x-honeycomb-team=${HONEYCOMB_API_KEY}" \
  -Dotel.service.name=order-service \
  -Dotel.metrics.exporter=none \
  -Dotel.traces.sampler=traceidratio \
  -Dotel.traces.sampler.arg=0.1 \
  -Dotel.instrumentation.jdbc.statement-sanitizer.enabled=true \
  -jar build/libs/order-service.jar
```

**Python (Delivery Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Standard OpenTelemetry with full control
pip install -q opentelemetry-distro opentelemetry-exporter-otlp

opentelemetry-instrument \
  --exporter_otlp_endpoint https://api.honeycomb.io:443 \
  --exporter_otlp_headers "x-honeycomb-team=${HONEYCOMB_API_KEY}" \
  --service_name delivery-service \
  --traces_exporter otlp \
  --metrics_exporter none \
  python main.py
```

**Go (Restaurant Service) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Standard OTEL configuration
OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443 \
OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}" \
OTEL_SERVICE_NAME=restaurant-service \
OTEL_TRACES_SAMPLER=traceidratio \
OTEL_TRACES_SAMPLER_ARG=0.1 \
./restaurant-service
```

**Node.js (Web UI) - run.sh**:
```bash
#!/bin/bash
source ../../.env

# Standard OpenTelemetry setup
OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443 \
OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=${HONEYCOMB_API_KEY}" \
OTEL_SERVICE_NAME=web-ui \
OTEL_TRACES_SAMPLER=traceidratio \
OTEL_TRACES_SAMPLER_ARG=0.1 \
node --require ./tracing.js server.js
```

## GitHub Codespaces Configuration

### .devcontainer/devcontainer.json
```json
{
  "name": "Food Delivery Observability Tutorial",
  "image": "mcr.microsoft.com/devcontainers/universal:linux",
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "17"
    },
    "ghcr.io/devcontainers/features/go:1": {
      "version": "1.21"
    },
    "ghcr.io/devcontainers/features/python:1": {
      "version": "3.11"
    },
    "ghcr.io/devcontainers/features/node:1": {
      "version": "18"
    }
  },
  "forwardPorts": [8080, 8081, 8082, 8083],
  "postCreateCommand": "bash .devcontainer/setup.sh",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "golang.go",
        "ms-python.python",
        "dbaeumer.vscode-eslint"
      ]
    }
  }
}
```

### .devcontainer/setup.sh
```bash
#!/bin/bash

# Install dependencies
echo "Installing dependencies..."

# Java dependencies - Download BOTH agents
cd /workspace/lib
# Honeycomb distribution for lessons 001-005
wget https://github.com/honeycombio/honeycomb-opentelemetry-java/releases/latest/download/honeycomb-opentelemetry-javaagent.jar
# Standard OpenTelemetry for lesson 006
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.32.0/opentelemetry-javaagent.jar

# Python dependencies
# Honeycomb distribution for lessons 001-005
pip install honeycombio-opentelemetry
# Standard OpenTelemetry for lesson 006
pip install opentelemetry-distro opentelemetry-exporter-otlp opentelemetry-instrumentation-fastapi

# Go dependencies
go install github.com/gin-gonic/gin@latest
go get go.opentelemetry.io/otel
go get go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp
go get github.com/honeycombio/honeycomb-opentelemetry-go

# Node dependencies
npm install -g nodemon
# Install both Honeycomb and standard OTEL packages globally
npm install -g @honeycombio/opentelemetry-node
npm install -g @opentelemetry/api @opentelemetry/auto-instrumentations-node

# Setup SQLite databases
mkdir -p /workspace/data
sqlite3 /workspace/data/orders.db < /workspace/scripts/schema.sql

# Create .env file if not exists
if [ ! -f /workspace/.env ]; then
    echo "HONEYCOMB_API_KEY=" > /workspace/.env
    echo "Please add your Honeycomb API key to .env file"
fi

echo "Setup complete!"
echo "Lessons 001-005 use Honeycomb OpenTelemetry distribution (simpler config)"
echo "Lesson 006 uses standard OpenTelemetry (vendor-neutral)"
```

## Migration Strategy from Current Tutorial

### Phase 1: Preserve Existing Structure
1. Keep lessons 001-006 in current form
2. Create new folder `food-delivery/` for new implementation
3. Maintain backward compatibility

### Phase 2: Parallel Implementation
1. Build food delivery system in new folder
2. Test with students in pilot session
3. Gather feedback and iterate

### Phase 3: Gradual Migration
1. Replace one lesson at a time
2. Update documentation
3. Create migration guide for instructors

### Phase 4: Full Cutover
1. Archive old joke API to `legacy/`
2. Move food delivery to main folders
3. Update all references and README

## Required Files Structure

```
observability-tutorial/
├── FOOD_DELIVERY_PLAN.md (this file)
├── README.md
├── .env
├── .devcontainer/
│   ├── devcontainer.json
│   └── setup.sh
├── lib/
│   ├── honeycomb-opentelemetry-javaagent.jar
│   └── opentelemetry-javaagent.jar
├── 000-baseline/
│   ├── order-service/
│   ├── restaurant-service/
│   ├── delivery-service/
│   ├── web-ui/
│   └── README.md
├── 001-cross-service/
│   ├── order-service/
│   │   ├── build.gradle
│   │   ├── src/main/java/...
│   │   └── run.sh
│   ├── restaurant-service/
│   │   ├── go.mod
│   │   ├── main.go
│   │   └── run.sh
│   ├── delivery-service/
│   │   ├── requirements.txt
│   │   ├── main.py
│   │   └── run.sh
│   ├── web-ui/
│   │   ├── package.json
│   │   ├── server.js
│   │   ├── public/
│   │   └── run.sh
│   └── README.md
├── 002-manual-spans/
│   └── (similar structure with enhanced code)
├── 003-events/
│   └── (similar structure with event additions)
├── 004-async/
│   └── (similar structure with queue processing)
├── 005-links/
│   └── (similar structure with trace linking)
├── 006-performance/
│   └── (similar structure with optimizations)
├── 007-advanced-otel/
│   └── (similar structure with standard OpenTelemetry)
└── scripts/
    ├── load-test.sh
    ├── chaos-enable.sh
    └── schema.sql
```

## Success Metrics

### Technical Metrics
- All services start in < 30 seconds on Codespaces
- Memory usage < 2GB total
- Can handle 100 concurrent orders
- Traces visible in Honeycomb within 5 seconds

### Educational Metrics
- Students can identify bottlenecks within 15 minutes
- 90% can complete debugging exercises
- Clear understanding of spans vs events vs links
- Can implement basic performance optimizations

## Next Steps

1. Review and approve this plan
2. Set up new repository structure
3. Implement Order Service (Java)
4. Implement Restaurant Service (Go)
5. Implement Delivery Service (Python)
6. Create Web UI
7. Write comprehensive tests
8. Create student exercise workbooks
9. Record demo videos
10. Pilot with test group

## Notes for Implementation

- Start with 001-cross-service as it's the foundation
- Each service should have health check endpoints
- Include correlation IDs in all requests
- Add README in each lesson folder with learning objectives
- Create troubleshooting guide for common issues
- Include sample Honeycomb queries for each exercise
- Add graceful shutdown handlers for Codespaces
- Implement service discovery via environment variables
- Use docker-compose for local development option

## Benefits of the Honeycomb Distribution → Standard OTEL Progression

### For Students
1. **Gentle Learning Curve**: Start with minimal configuration (2 env vars) in lessons 001-005
2. **Focus on Concepts First**: Less time debugging configuration, more time understanding traces
3. **Clear Error Messages**: Honeycomb distribution provides better guidance when things go wrong
4. **Advanced Topics Later**: Learn vendor-neutral approaches and complex configuration in lesson 006

### For Instructors
1. **Reduced Setup Issues**: Fewer configuration problems in early lessons
2. **Clear Teaching Path**: Simple → Complex progression
3. **Real-World Skills**: Students learn both vendor-specific and vendor-neutral approaches
4. **Debugging Practice**: Lesson 006 migration provides troubleshooting opportunities

### Technical Advantages
1. **Both Approaches Covered**: Students understand trade-offs between convenience and flexibility
2. **Production-Ready Knowledge**: Learn when to use each approach
3. **Cost Optimization**: Understand sampling strategies to control data volume
4. **Multi-Vendor Skills**: Lesson 006 knowledge transfers to Jaeger, Zipkin, Datadog, etc.

This plan provides a complete blueprint for implementing the food delivery observability tutorial. The system is designed to be educational, realistic, and practical while running efficiently in GitHub Codespaces, with a progressive learning path from simple Honeycomb-specific setup to advanced vendor-neutral OpenTelemetry configuration.