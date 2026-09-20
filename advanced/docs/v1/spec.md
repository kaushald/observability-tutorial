# Food Delivery Observability Tutorial - Specification

## Purpose

Build a hands-on observability learning platform that teaches distributed system monitoring through a realistic food delivery application. Students will experience the evolution from debugging without observability to mastering advanced tracing techniques.

## Why This System?

### The Problem We're Solving

Observability is abstract until you experience its absence. Students often learn tools without understanding the problems they solve. This tutorial creates that understanding through controlled pain followed by progressive relief.

### Why Food Delivery?

- **Universally relatable**: Everyone understands ordering food online
- **Natural complexity**: Multiple services, async operations, real-time updates
- **Clear failure modes**: Late delivery, wrong items, payment issues - all familiar problems
- **Business vs system errors**: Restaurant closed (business) vs service timeout (system)
- **Performance matters**: Users abandon slow checkouts

## The Learning Journey

### Lesson 000: Debugging in the Dark

**Experience**: Place an order that fails mysteriously
**Reality**: Four services, four log files, zero correlation
**Learning**: Why logs alone aren't enough for distributed systems

### Lesson 001: First Light - Cross-Service Tracing

**Experience**: See the complete order flow across all services
**Reality**: Trace IDs connect the dots between services
**Learning**: How distributed tracing reveals system behavior

### Lesson 002: Deeper Visibility - Manual Spans

**Experience**: Identify exactly which database query is slow
**Reality**: Custom spans expose internal operations
**Learning**: How to instrument business logic for visibility

### Lesson 003: Capturing Context - Events

**Experience**: Track order state changes and business decisions
**Reality**: Events tell the story within a trace
**Learning**: Difference between technical spans and business events

### Lesson 004: Async Complexity - Queue Processing

**Experience**: Debug why some notifications never arrive
**Reality**: Async operations need special handling
**Learning**: Tracing through queues and background jobs

### Lesson 005: Connecting Stories - Trace Links

**Experience**: Link refunds to original orders
**Reality**: Related operations need explicit connections
**Learning**: How to maintain context across independent workflows

### Lesson 006: Performance Mastery - Optimization

**Experience**: Find and fix N+1 queries, cache stampedes
**Reality**: Traces guide optimization decisions
**Learning**: Using observability data to improve performance

### Lesson 007: Production Ready - Standard OpenTelemetry

**Experience**: Migrate from simplified to vendor-neutral setup
**Reality**: Trade convenience for flexibility
**Learning**: When to use vendor distributions vs standard OTEL

## User Personas

### Primary: The Student Developer

- Has built web applications but not distributed systems
- Knows basic debugging (console.log, debugger)
- Wants to understand modern cloud architectures
- Needs practical experience with production patterns

### Secondary: The Instructor

- Teaching distributed systems or SRE practices
- Needs ready-to-use exercises with clear outcomes
- Wants students to experience real problems
- Requires minimal setup and maintenance

## Success Criteria

### Technical Success

- Student can identify root cause in < 5 minutes (vs 30+ without observability)
- All services start within 30 seconds on free Codespaces
- System handles 100 concurrent orders for load testing
- Traces appear in UI within 5 seconds

### Educational Success

- Students experience the "aha!" moment when traces reveal hidden problems
- Clear progression from confusion to mastery
- Mistakes are instructive, not frustrating
- Knowledge transfers to any distributed system

### Practical Success

- Students can implement observability in their own projects
- Understanding of costs vs benefits (sampling strategies)
- Ability to debug production issues
- Knowledge applies to any observability platform

## Core Experiences

### The "Order Stuck" Mystery

Without observability: Check four services, guess at timing, hope for logs
With observability: Single trace shows restaurant service timeout

### The "Lunch Rush" Meltdown

Without observability: "It's slow" - but which part?
With observability: Database connection pool exhaustion at 12:30 PM

### The "Missing Notifications" Case

Without observability: Notifications sometimes fail, no pattern visible
With observability: Async queue drops messages with malformed phone numbers

### The "Payment Spike" Investigation

Without observability: Payment slow every hour, seems random
With observability: Cache TTL causes synchronized expiration

## What This Is Not

- Not a production food delivery system
- Not a comprehensive OpenTelemetry reference
- Not a performance testing framework
- Not a specific vendor's tutorial
- Not a microservices architecture course

## Constraints & Assumptions

### Technical Constraints

- Must run on free GitHub Codespaces (2 core, 4GB RAM)
- No external databases (SQLite only)
- No real payment processing
- No actual delivery logistics
- Single-region, no geo-distribution

### Educational Constraints

- 2-hour workshop timeframe
- No prior observability knowledge required
- Basic programming skills assumed
- English documentation only
- Sequential lesson completion expected

## Measuring Success

### Immediate Indicators

- Time to first trace: < 2 minutes
- Time to find injected bug: < 10 minutes
- Successful completion rate: > 90%
- Error message clarity: No cryptic failures

### Learning Indicators

- Can explain spans vs events vs links
- Can implement custom instrumentation
- Can optimize based on trace data
- Can choose appropriate sampling strategies

### Long-term Indicators

- Students implement observability in own projects
- Students debug production issues faster
- Teams adopt structured debugging approaches
- Reduced mean time to resolution (MTTR)

## The Promise

By completing this tutorial, students will transform from developers who guess at problems to engineers who see exactly what's happening in their distributed systems. They'll never want to debug without observability again.
