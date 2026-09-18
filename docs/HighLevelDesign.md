# High-level design

Companion diagrams for [`overview.md`](./overview.md) §3. Component names and
event ordering are taken from the actual package structure
(`com.urlshortener.controller`, `.service`, `.event`, `.ratelimit`) — see
[`decisions.md`](./decisions.md) for the specific class inside each layer and why
it exists.

## Components

Dashed = Phase 2 (scoped, not built — [`requirements.md`](./requirements.md) §6).
Each box is a layer, not a single class.

```mermaid
flowchart TB
    UI["Frontend (React + Vite)"]
    NGINX["nginx — TLS, static assets, edge rate limit"]
    API["Controllers + rate-limit check"]
    SVC["Service layer — validate, authorize, generate code"]
    REPO["Repositories"]
    EVT["Click pipeline — publish, buffer, drain, broadcast"]
    PG[("PostgreSQL")]
    REDIS[("Redis")]
    GOOGLE[["Google OAuth"]]

    UI --> NGINX -.-> API
    API --> SVC
    SVC --> REPO --> PG
    SVC --> EVT
    EVT --> REDIS
    EVT --> PG
    EVT -. "live click count" .-> UI
    API --> REDIS
    API --> GOOGLE

    classDef phase2 stroke-dasharray: 4 3,color:#666;
    class NGINX phase2;
```

## Request flow

The two flows an interviewer will actually trace: creating a link, and what happens
when someone visits it.

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant API as Controller layer
    participant Svc as Service layer
    participant DB as PostgreSQL
    participant Click as Click pipeline

    Browser->>API: POST /api/urls {url, alias?, expiry?}
    API->>API: rate-limit check (create budget)
    API->>Svc: create(request, owner)
    Svc->>Svc: validate url + alias, generate code if none given
    Svc->>DB: insert url row
    DB-->>Svc: persisted
    Svc-->>API: short code
    API-->>Browser: 201 {shortCode}

    Note over Browser,DB: later, someone opens the short link

    Browser->>API: GET /{shortCode}
    API->>API: rate-limit check (redirect budget)
    API->>Svc: resolve(shortCode)
    Svc->>DB: select url where code = ?
    DB-->>Svc: row (active / expired / deleted)
    Svc-->>API: target, or 404/410
    API->>Click: publish click event
    API-->>Browser: 302 Location: target
    Click->>Click: buffer in Redis, drain on schedule, persist to PostgreSQL
    Click-->>Browser: STOMP /topic/clicks/{code} (if analytics view open)
```
