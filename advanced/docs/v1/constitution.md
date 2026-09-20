# Food Delivery Observability Tutorial - Constitution

## Core Principles

These principles are immutable and guide every decision in this project. When faced with trade-offs, these principles provide the resolution.

## 1. Education First

**Principle**: Every technical decision must enhance learning outcomes.

**In Practice**:
- Choose readable code over clever optimizations
- Select familiar technologies over exotic ones
- Implement clear failures over silent degradation
- Add instructive comments over assuming knowledge

**Example Decisions**:
- ✅ Use SQLite (simple) over PostgreSQL (complex setup)
- ✅ Implement visible errors over graceful fallbacks
- ✅ Choose Bootstrap over custom CSS
- ❌ Don't add Kubernetes complexity for container orchestration

## 2. Progressive Complexity

**Principle**: Start with zero, build incrementally, never skip steps.

**In Practice**:
- Lesson 000 must work without any observability
- Each lesson adds exactly one concept
- Later lessons build on earlier foundations
- No forward references to future concepts

**Example Decisions**:
- ✅ Lesson 000: Only console.log, no trace IDs
- ✅ Lesson 001: Add automatic instrumentation only
- ✅ Lesson 002: Then add manual spans
- ❌ Don't mention sampling in lesson 001

## 3. Real-World Relevance

**Principle**: Every scenario must reflect actual production problems.

**In Practice**:
- Use realistic failure modes (timeouts, pool exhaustion)
- Implement actual business workflows (payment → fulfillment → delivery)
- Include genuine performance problems (N+1 queries, cache stampedes)
- Create believable user experiences (ordering food)

**Example Decisions**:
- ✅ Implement connection pool exhaustion
- ✅ Add random network timeouts
- ✅ Include malformed data handling
- ❌ Don't create artificial puzzles with no real-world equivalent

## 4. Accessibility Above All

**Principle**: Must run on free GitHub Codespaces without modification.

**In Practice**:
- Total memory usage < 2GB
- All services start in < 30 seconds
- No external service dependencies
- Single git clone to start

**Example Decisions**:
- ✅ Use in-memory stores over external databases
- ✅ Embed SQLite over requiring PostgreSQL
- ✅ Include all agents in the repo
- ❌ Don't require Docker Desktop or cloud accounts

## 5. Observability as Foundation

**Principle**: Observability is not added to the system; the system is built on observability.

**In Practice**:
- Traces drive debugging, not logs
- Performance problems visible in traces
- Business logic instrumented by default
- Observability guides optimization

**Example Decisions**:
- ✅ Instrument cache operations from the start
- ✅ Add trace context to all service calls
- ✅ Include timing in every span
- ❌ Don't treat tracing as optional debug mode

## 6. Failure as Teacher

**Principle**: Controlled failures teach better than perfect systems.

**In Practice**:
- Inject realistic failures deliberately
- Make failures discoverable through traces
- Ensure failures have clear solutions
- Create failures that mirror production

**Example Decisions**:
- ✅ Add chaos middleware for controlled failures
- ✅ Implement timeout scenarios
- ✅ Create intermittent connection issues
- ❌ Don't create random, unexplainable failures

## 7. Vendor Flexibility

**Principle**: Teach universal concepts, not vendor products.

**In Practice**:
- Start with vendor simplicity (Honeycomb, lessons 001-006)
- Graduate to vendor neutrality (Standard OTEL, lesson 007)
- Explain trade-offs explicitly
- Knowledge must transfer to any platform

**Example Decisions**:
- ✅ Use Honeycomb distribution for easy start
- ✅ Migrate to standard OpenTelemetry
- ✅ Show Jaeger as alternative viewer
- ❌ Don't hide vendor-specific magic

## 8. Self-Contained Lessons

**Principle**: Each lesson folder must be complete and runnable.

**In Practice**:
- No shared code between lessons
- Each lesson has its own README
- Services can run independently
- Clear setup and teardown

**Example Decisions**:
- ✅ Duplicate service code per lesson
- ✅ Include run scripts in each folder
- ✅ Self-contained documentation
- ❌ Don't require running previous lessons first

## 9. Clarity Over Cleverness

**Principle**: Explicit is better than implicit, simple is better than complex.

**In Practice**:
- Name things what they are
- Use descriptive variable names
- Add comments for non-obvious code
- Choose boring technology

**Example Decisions**:
- ✅ Name it "order-service" not "omicron"
- ✅ Use REST over GraphQL
- ✅ Implement clear service boundaries
- ❌ Don't use microservice patterns for their own sake

## 10. Fast Feedback Loops

**Principle**: Students must see results immediately.

**In Practice**:
- Traces visible within 5 seconds
- Services start quickly
- Changes reflect immediately
- Errors surface promptly

**Example Decisions**:
- ✅ Use live reload for code changes
- ✅ Show traces in real-time
- ✅ Implement health endpoints
- ❌ Don't batch traces for minutes

## Decision Framework

When making any decision, ask these questions in order:

1. **Does it improve learning?** If no, reject.
2. **Is it simple enough for lesson 000?** If not for lesson 000, defer to appropriate lesson.
3. **Does it reflect real-world patterns?** If not, redesign.
4. **Will it run on free Codespaces?** If not, find alternative.
5. **Does it demonstrate observability value?** If not, reconsider.
6. **Can students debug it with traces?** If not, add instrumentation.
7. **Is it vendor-neutral knowledge?** If vendor-specific, document clearly.
8. **Is the lesson still self-contained?** If not, refactor.
9. **Is it immediately obvious?** If not, simplify.
10. **Will students see results quickly?** If not, optimize.

## Conflict Resolution

When principles conflict, this hierarchy applies:

1. **Accessibility** - Must work for everyone
2. **Education** - Must teach effectively
3. **Simplicity** - Must be understandable
4. **Reality** - Must reflect production
5. **Speed** - Must provide quick feedback

## Anti-Patterns to Avoid

### The Kitchen Sink
Adding every observability feature because we can. Each lesson has ONE focus.

### The Perfect System
Creating a system that never fails. Failure teaches better than success.

### The Vendor Lock
Teaching product features instead of concepts. Knowledge must be portable.

### The Prerequisite Chain
Requiring completion of all previous lessons. Each lesson stands alone.

### The Production System
Building a real food delivery platform. This is education, not a startup.

### The Genius Bar
Requiring deep expertise to understand. If it needs a PhD, it's too complex.

### The Magic Show
Hiding complexity without explaining it. Students must understand what's happening.

### The Infinite Wait
Making students wait minutes for results. Feedback must be immediate.

## Practical Applications

### Adding a New Feature
Ask: "Which lesson needs this?" Don't add to lesson 001 what belongs in lesson 005.

### Choosing a Technology
Ask: "Will students encounter this in production?" Use boring, common technology.

### Designing an Exercise
Ask: "What real problem does this simulate?" Every exercise mirrors production.

### Writing Documentation
Ask: "Can a junior developer understand this?" If not, simplify.

### Handling Complexity
Ask: "Can we teach this in two steps instead of one?" Break it down.

### Optimizing Performance
Ask: "Does this help or hinder learning?" Sometimes slow is instructive.

## Enforcement

These principles are enforced through:

1. **Code Review** - Every PR checked against principles
2. **Student Testing** - Regular validation with target audience
3. **Instructor Feedback** - Workshop leaders report friction
4. **Automated Checks** - CI validates resource usage
5. **Documentation Review** - Ensures clarity and completeness

## Evolution

These principles are immutable for the core tutorial. Extensions or advanced modules may relax specific constraints but must explicitly document why and how they differ.

## The Sacred Truth

If forced to choose only one principle, choose this:

**A student who completes lesson 000 and 001 has learned more about observability than someone who reads 100 blog posts about it.**

Experience teaches better than explanation. This tutorial exists to create that experience.