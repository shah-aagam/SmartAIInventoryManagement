# StockPulse Architecture Decisions

## 1. Commerce boundary
Commerce decisions live behind `CommerceAdvisor`; HTTP requests and async inventory events use the same contract. Persistence and approval side effects stay in the application service.

## 2. Unified recommendation
One advisor call returns pricing and replenishment together. Both use the same product/velocity/trigger context, which keeps latency and prompt cost low. Each persisted suggestion still has its own approval state.

## 3. Runtime strategy switching
The service keeps a registry of `rule` and `ai` advisors and exposes `PUT /strategy`. A future competitor-aware advisor can implement the same interface and register under another key without changing callers.

## 4. AI gateway isolation
The provider-specific HTTP details are isolated in `LLMGateway`. The commerce layer knows only that it can send a prompt and receive model output. Configuration supplies the LiteLLM base URL, model, product header and API key through environment variables.

## 5. AI resilience
The AI advisor parses the model's JSON response and validates price bounds, direction, reorder quantity, confidence and reasoning. Any timeout, gateway failure, malformed JSON or unsafe recommendation falls back to `RuleBasedCommerceAdvisor`. The async path therefore never silently drops a recommendation.

## 6. Human checkpoint
AI/rule output is persisted as `PENDING`. Accepting a pricing suggestion updates `Product.currentPrice`; accepting a reorder suggestion increments `Product.stockLevel`. The recommendation layer never directly applies a live commerce decision.

## 7. Agentic loop
Stock/order mutations publish `InventorySignal`. A `@TransactionalEventListener(AFTER_COMMIT)` receives the event and an `@Async` handler creates recommendations after the inventory transaction has committed. This avoids making the HTTP request wait for LLM latency and avoids the race where an async consumer reads uncommitted state.

## 8. Idempotency
Pricing and reorder suggestions independently prevent duplicate `PENDING` suggestions for the same product + trigger + suggestion type. This matters because multiple inventory changes can produce the same signal before merchandising acts.

## 9. API semantics
The automatic event path creates both suggestion types because the business loop needs both pricing and replenishment decisions. The two on-demand endpoints create only the requested suggestion type so their HTTP semantics remain explicit.

## Deferred
Authentication, supplier integrations, competitor scraping, PostgreSQL deployment, and SSE are intentionally deferred to preserve the core observe → reason → act → checkpoint loop.
