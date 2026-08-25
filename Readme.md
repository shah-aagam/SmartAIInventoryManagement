# StockPulse

## Overview

StockPulse is an automated inventory management and smart commerce decision-support system built for retail and supply-chain monitoring. The system continuously tracks product stock levels, sales velocity, and reorder thresholds to dynamically generate dynamic pricing adjustments and stock replenishment recommendations.

The architecture supports dual recommendation strategies:
- **Rule-Based Strategy:** Algorithmic heuristic rules evaluating reorder thresholds and price shifts based on velocity and stock metrics.
- **AI-Driven Strategy:** External Large Language Model (LLM) integration via LiteLLM to compute price directions, recommended quantities, confidence scores, and reasoning.

## Features

- **Product Management:** Create products with validation constraints (positive price, non-negative stock levels, non-negative reorder threshold, non-negative demand velocity) and query/filter product status (`ACTIVE`, `OUT_OF_STOCK`) and category.
- **Stock & Order Processing:** Adjust product stock levels and record customer orders against inventory, triggering inventory updates.
- **Dynamic Pricing Suggestions:** Automatic event-driven or manual (`MANUAL` trigger) price adjustment recommendation generation, review, and decision workflow (approve/reject).
- **Reorder Suggestions:** Event-driven threshold triggers or manual requests to generate stock reorder recommendations and process approval workflows.
- **Asynchronous Inventory Signals:** Event-driven architecture using Spring async event listeners to evaluate commerce suggestions without blocking main REST requests.
- **Dual Commerce Advisory Engine:** Toggleable strategy execution between rule heuristics and external LLM integration.

## Architecture

StockPulse uses a decoupled full-stack client-server architecture:

```text
┌────────────────────────────────────────────────────────────────────────┐
│                          Frontend Client                               │
│  React 19 + Vite 8 SPA (Tailwind CSS 4) running on localhost:5173      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ REST APIs / JSON over HTTP
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          Backend Service                               │
│  Spring Boot 3.3.5 Application (Java 17)                               │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ REST Controller Layer (ApiController)                             │  │
│  └────────────────────────────────┬─────────────────────────────────┘  │
│                                   │                                    │
│  ┌────────────────────────────────▼─────────────────────────────────┐  │
│  │ Business & Strategy Layer (StockPulseService)                    │  │
│  │  - Commerce Strategy Selector ("rule" vs "ai")                   │  │
│  │  - Event Dispatcher (ApplicationEventPublisher)                  │  │
│  └─────────────────┬──────────────────────────────┬─────────────────┘  │
│                    │                              │                    │
│  ┌─────────────────▼─────────────┐   ┌────────────▼──────────────────┐ │
│  │ Spring Data JPA Repository    │   │ External Integration          │ │
│  │ - ProductRepository           │   │ - LLMGateway                  │ │
│  │ - PricingSuggestionRepo       │   │   (LiteLLM API integration)   │ │
│  │ - ReorderSuggestionRepo       │   └───────────────────────────────┘ │
│  └─────────────────┬─────────────┘                                     │
└────────────────────┼───────────────────────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       In-Memory H2 Database                            │
│  Schema: Created dynamically at startup (`create-drop`)                │
│  Initial Data: Standard dataset loaded via `data.sql`                  │
└────────────────────────────────────────────────────────────────────────┘
```

### Architectural Design Patterns
- **Strategy Pattern:** Implemented via `CommerceAdvisor` interface with concrete implementations `RuleBasedCommerceAdvisor` and `AiCommerceAdvisor`.
- **Event-Driven Architecture:** `InventorySignal` emitted on inventory changes and consumed asynchronously by `InventorySignalHandler` (`@EnableAsync`).
- **Repository Pattern:** Persistence layer mediated via Spring Data JPA interfaces.

## Tech Stack

### Backend
- **Language:** Java 17
- **Framework:** Spring Boot 3.3.5
- **Web Layer:** Spring Web (`spring-boot-starter-web`)
- **Persistence Layer:** Spring Data JPA (`spring-boot-starter-data-jpa`)
- **Validation:** Jakarta Validation (`spring-boot-starter-validation`)
- **Database:** In-Memory H2 Database (`com.h2database:h2`)
- **Build Tool:** Apache Maven (Maven Wrapper included)

### Frontend
- **Framework/Library:** React 19 (`react`: `^19.2.8`, `react-dom`: `^19.2.8`)
- **Build Tool:** Vite 8 (`vite`: `^8.2.2`, `@vitejs/plugin-react`: `^6.1.0`)
- **Styling:** Tailwind CSS 4 (`tailwindcss`: `^4.3.3`, PostCSS, Autoprefixer)
- **Linter:** ESLint 10 (`eslint`: `^10.9.0`)

## Installation

Not specified in the repository.

## Configuration

### Database Configuration
Configured in `src/main/resources/application.properties`:
- **Database URL:** `jdbc:h2:mem:stockpulse`
- **DDL Strategy:** `spring.jpa.hibernate.ddl-auto=create-drop`
- **Data Initialization:** `spring.jpa.defer-datasource-initialization=true` (loads `data.sql` on startup)
- **H2 Console:** Accessible at `/h2-console` (`spring.h2.console.enabled=true`)

### External Integration (LiteLLM AI Gateway)
External LLM services are configured via `application.properties`:
- `LLM_BASE_URL`: Gateway base URL (Default: `https://litellm-qc.zycus.net`)
- `LLM_API_KEY`: API authentication key
- `LLM_MODEL`: Model name (Default: `qwen-cursor`)
- `LLM_PRODUCT`: Product identifier (Default: `PC1`)
- `LLM_COOKIE`: Session cookie configuration (optional)

### CORS Settings
The backend controller explicitly allows cross-origin requests from `http://localhost:5173` (`@CrossOrigin(origins = "http://localhost:5173")`).

## Usage

### REST API Endpoints

| Category | Method | Endpoint | Description / Body |
| :--- | :--- | :--- | :--- |
| **Products** | `GET` | `/products` | Filter by query parameters: `status`, `category` |
| | `POST` | `/products` | Create product (`CreateProductRequest`) |
| **Inventory** | `PATCH` | `/products/{id}/stock` | Update product stock level (`StockRequest`) |
| | `POST` | `/products/{id}/orders` | Record customer order against product (`OrderRequest`) |
| **Suggestions** | `POST` | `/products/{id}/suggest-pricing` | Trigger manual dynamic pricing recommendation |
| | `POST` | `/products/{id}/suggest-reorder` | Trigger manual stock reorder recommendation |
| | `GET` | `/pricing-suggestions` | Retrieve dynamic pricing suggestions |
| | `GET` | `/reorder-suggestions` | Retrieve stock reorder suggestions |
| **Decisions** | `PATCH` | `/pricing-suggestions/{id}` | Approve or reject pricing recommendation (`DecisionRequest`) |
| | `PATCH` | `/reorder-suggestions/{id}` | Approve or reject reorder recommendation (`DecisionRequest`) |

## Project Structure

```text
.
├── StockPulse-fixed - Copy/ZCHackathon/
│   ├── src/main/java/com/example/ZCHackathon/
│   │   ├── ZcHackathonApplication.java
│   │   ├── api/
│   │   │   └── ApiController.java
│   │   ├── service/
│   │   │   └── StockPulseService.java
│   │   ├── product/
│   │   │   └── ProductRepository.java
│   │   ├── suggestion/
│   │   │   ├── PricingSuggestionRepository.java
│   │   │   └── ReorderSuggestionRepository.java
│   │   └── ai/
│   │       └── AiRecommendationPayload.java
│   └── src/main/resources/
│       ├── application.properties
│       └── data.sql
└── frontend/
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.jsx
        ├── App.jsx
        ├── App.css
        └── index.css
```
