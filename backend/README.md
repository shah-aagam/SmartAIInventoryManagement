# StockPulse

Reactive inventory and dynamic-pricing console for the interview brief.

## Stack

- Java 17
- Spring Boot 3.3.5
- Spring Web / REST
- Spring Data JPA + Hibernate
- H2 (in-memory for the challenge)
- React 18/Vite frontend can consume the REST API
- OpenAI-compatible LiteLLM gateway with `qwen-cursor`

## Run

Backend (requires JDK 17+ and Maven):

```bash
mvn spring-boot:run
```

The repository also contains `mvnw` / `mvnw.cmd` for Maven Wrapper environments.

### LLM configuration

Never commit the API key. Configure it as an environment variable.

PowerShell:

```powershell
$env:LLM_API_KEY="<provided-key>"
$env:LLM_BASE_URL="https://litellm-qc.zycus.net"
$env:LLM_MODEL="qwen-cursor"
$env:LLM_PRODUCT="PC1"
# Only if the gateway requires the provided cookie:
# $env:LLM_COOKIE="<provided-cookie>"
```

The backend sends:

```http
POST /v1/chat/completions
Authorization: Bearer <LLM_API_KEY>
Content-Type: application/json
product: PC1
```

with the configured model and a single user message containing the commerce prompt.

## API

### Products

```text
POST  /products
GET   /products?status=ACTIVE&category=APPAREL
PATCH /products/{id}/stock
POST  /products/{id}/orders
```

### On-demand recommendations

```text
POST /products/{id}/suggest-pricing
POST /products/{id}/suggest-reorder
```

These endpoints create only the requested suggestion type. The automatic agentic loop creates both pricing and reorder suggestions together.

### Review queues

```text
GET   /pricing-suggestions
GET   /reorder-suggestions
PATCH /pricing-suggestions/{id}
PATCH /reorder-suggestions/{id}
```

Decision body:

```json
{"status":"ACCEPTED"}
```

or

```json
{"status":"REJECTED"}
```

### Runtime strategy

```text
GET /strategy
PUT /strategy
```

Example:

```json
{"active":"ai"}
```

Switching between `rule` and `ai` does not require a restart.

## Automatic agentic path

```text
POST /products/{id}/orders
        |
        v
Product stock/velocity changes
        |
        v
InventorySignal published
        |
        v
@TransactionalEventListener(AFTER_COMMIT) + @Async
        |
        v
CommerceAdvisor
   /             \
rule              ai
                  |
                  v
              LiteLLM/Qwen
        |
        v
validate recommendation
        |
        +--> invalid/timeout --> rule fallback
        |
        v
PricingSuggestion + ReorderSuggestion (PENDING)
        |
        v
Human accepts/rejects
```

The AI path validates price bounds, direction, quantity, confidence and reasoning. A malformed/unsafe/timeout response falls back to the deterministic rule strategy instead of silently dropping the recommendation.

## Demo path

1. Start with the `rule` strategy and call `GET /products`.
2. Simulate a sale for `PRD-003` (Organic Cotton T-Shirt), which is seeded at stock 8 and threshold 15.
3. The stock-low event is handled asynchronously.
4. The review queues populate with pricing and reorder suggestions.
5. Switch to AI with `PUT /strategy` and repeat the flow to see an LLM-backed recommendation.
6. Accept the pricing suggestion and observe `currentPrice` change.
7. Accept the reorder suggestion and observe `stockLevel` increase.

## Deliberate design choices

- `CommerceAdvisor` is the strategy boundary shared by HTTP and async callers.
- One advisor call returns both pricing and reorder recommendations; persistence keeps their approval states separate.
- Inventory signals are handled after the originating transaction commits, then asynchronously.
- Pending suggestions are idempotent per product + trigger + suggestion type.
- AI is advisory only; it never changes live price or stock without an explicit human acceptance.
- H2 is used to keep setup fast; persistence is through JPA so PostgreSQL can be introduced later.
