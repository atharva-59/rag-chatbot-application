# 🤖 DocuMind - Enterprise RAG Chatbot

> A production-grade **Retrieval-Augmented Generation (RAG)** service built with **Spring Boot** and **Spring AI**. Ingest PDFs and web pages, then ask grounded, cited questions answered by Google **Gemini** over a **pgvector** knowledge base.

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" />
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen" />
  <img src="https://img.shields.io/badge/Spring%20AI-2.0.1-blue" />
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-blueviolet" />
  <img src="https://img.shields.io/badge/LLM-Gemini-yellow" />
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" />
</p>

---

## 📑 Table of Contents
1. [What is this?](#-what-is-this)
2. [Why it stands out](#-why-it-stands-out)
3. [Key Features](#-key-features)
4. [Tech Stack & Rationale](#-tech-stack--why-we-chose-each)
5. [Architecture](#-architecture)
6. [How RAG works here](#-how-rag-works-here)
7. [Project Structure](#-project-structure)
8. [Getting Started](#-getting-started-run-it-locally)
9. [Configuration Reference](#-configuration-reference)
10. [API Endpoints](#-api-endpoints)
11. [Health & Monitoring](#-health--monitoring)
12. [Resilience: Model Fallback](#-resilience-the-model-fallback-chain)
13. [Adaptive Retrieval & Fan-out](#-adaptive-retrieval--query-fan-out)
14. [Running Tests](#-running-tests)
15. [Roadmap](#-roadmap--future-enhancements)
16. [License](#-license)

---

## 🎯 What is this?

**DocuMind** is a backend service that lets you **chat with your documents**. You feed it PDFs or web-page URLs; it splits them into chunks, converts them into vector embeddings, and stores them in a **PostgreSQL + pgvector** database. When you ask a question, it retrieves the most relevant chunks, feeds them as context to a **Gemini** LLM, and returns a **grounded answer with citations** — so the model answers *from your data*, not from its imagination.

- **Retrieval-Augmented Generation (RAG):** combines semantic vector search with LLM generation, so answers are accurate and grounded in real source material rather than hallucinated.
- **Spring AI:** the Spring-native framework that abstracts LLM/embedding providers, vector stores, and RAG plumbing into clean Java APIs.
- **pgvector:** a PostgreSQL extension adding vector similarity search — the engine behind our semantic retrieval.

---

## 🌟 Why it stands out

This isn't a toy demo. It's engineered like production software:

- ✅ **Resilient by design** - an automatic **model fallback chain** keeps the app answering even when the primary model is rate-limited.
- ✅ **Adaptive retrieval** - detects *broad* vs. *specific* questions and **fans out** broad queries into sub-queries for wider coverage.
- ✅ **Grounded & verifiable** - every answer ships with **deduplicated citations**.
- ✅ **Dual RAG modes** - an idiomatic Spring AI `QuestionAnswerAdvisor` path *and* a transparent, fully-logged manual pipeline, switchable by config.
- ✅ **Observable** - custom Actuator **health indicators** and **Micrometer metrics** (Prometheus-ready).
- ✅ **Clean architecture** - layered design, domain exceptions, centralized error handling.
- ✅ **Documented** - interactive **OpenAPI/Swagger UI** and a clickable demo page.

---

## ✨ Key Features

| Feature | Description |
|---|---|
| 📄 **PDF ingestion** | Upload PDFs; parsed page-by-page, chunked, embedded, and stored |
| 🌐 **URL ingestion** | Fetch & extract readable text from web pages (Jsoup) |
| 💬 **Grounded Q&A** | Ask questions; get answers strictly from ingested content |
| 📌 **Citations** | Every answer returns deduplicated sources with snippets & scores |
| 🔀 **Query fan-out** | Broad/summary questions expand into multiple sub-queries for coverage |
| 🛡️ **Model fallback chain** | Auto-rotates across Gemini models on rate-limit/failure (zero-downtime) |
| ♻️ **Retry + backoff** | Exponential-backoff retries on transient failures + request timeouts |
| ⚙️ **Dual RAG modes** | `advisor` (idiomatic) or `manual` (transparent, logged) via config |
| 🌊 **Streaming** | Server-Sent Events (SSE) token streaming |
| 📊 **Observability** | Custom health checks + metrics (`rag.queries`, `rag.ingested`, `rag.fallbacks`) |
| 🚨 **Clean errors** | Domain exceptions → consistent JSON via a global handler |
| 📖 **OpenAPI docs** | Interactive Swagger UI |
| 🖥️ **Demo UI** | Single-page clickable interface served from the app |

---

## 🧰 Tech Stack & Why We Chose Each

| Technology | Role | Why we chose it |
|---|---|---|
| **Java 21** | Language | Modern LTS: records, pattern matching, virtual-thread-ready |
| **Spring Boot 4.1** | Application framework | Industry-standard, batteries-included, production-grade |
| **Spring AI 2.0.1** | AI integration | Spring-native abstractions for LLMs, embeddings, vector stores & RAG - no vendor lock-in, clean Java APIs |
| **Google Gemini** | Chat + embeddings | Strong quality with a generous free tier; ideal for a self-hostable demo |
| **PostgreSQL + pgvector** | Vector database | Reuse a battle-tested RDBMS for vector similarity search - no separate vector DB to operate |
| **Apache PDFBox** (via Spring AI reader) | PDF parsing | Reliable page-level text extraction |
| **Jsoup** | Web scraping | Robust HTML fetch + readable-text extraction |
| **Micrometer + Actuator** | Observability | Standard metrics/health with Prometheus export |
| **SpringDoc OpenAPI** | API docs | Auto-generated, interactive Swagger UI |
| **JUnit 5 + Mockito** | Testing | Standard, reliable unit testing |
| **Maven** | Build | Ubiquitous, simple dependency management |

---

## 🏗 Architecture

```
                         ┌───────────────────────────────────────────────┐
                         │                 Client / UI                   │
                         │        (Demo page · Swagger · Postman)        │
                         └───────────────────────┬───────────────────────┘
                                                 │ HTTP / SSE
                         ┌───────────────────────▼───────────────────────┐
                         │               Controller Layer                │
                         │   IngestionController · RagController         │
                         │ GlobalExceptionHandler (@RestControllerAdvice)│
                         └───────────────────────┬───────────────────────┘
                                                 │
                         ┌───────────────────────▼───────────────────────┐
                         │                Service Layer                   │
                         │                                                │
                         │  IngestionService ──► chunk ─► embed ─► store  │
                         │                                                │
                         │  RagService (manual | advisor)                 │
                         │     ├─ RetrievalService (classify + fan-out +  │
                         │     │                     dedupe)              │
                         │     ├─ ResilientChatService (fallback + retry) │
                         │     └─ CitationMapper                          │
                         │                                                │
                         │  RagMetrics (Micrometer counters)              │
                         └───────────┬───────────────────────┬────────────┘
                                     │                        │
                    ┌────────────────▼──────┐     ┌───────────▼───────────┐
                    │   Google Gemini API   │     │  PostgreSQL + pgvector │
                    │  (chat + embeddings)  │     │   (vector knowledge    │
                    └───────────────────────┘     │        base)           │
                                                  └───────────────────────┘
```

**Layered separation of concerns:**
- **Controller** - HTTP handling, validation, error mapping only.
- **Service** - all business logic (ingestion, retrieval, generation, resilience).
- **Persistence** - pgvector via Spring AI's `VectorStore`.
- **Cross-cutting** - exceptions (`exception/`), health (`health/`), metrics (`metrics/`).

---

## 🔎 How RAG Works Here

```
  INGEST                                   QUERY
  ──────                                   ─────
  1. Upload PDF / URL                      1. User asks a question
  2. Extract text                          2. Classify: broad vs specific
  3. Split into chunks (size + overlap)    3. Retrieve top-K chunks (fan-out if broad)
  4. Generate embeddings (Gemini)          4. Deduplicate + rank
  5. Store vectors in pgvector             5. Augment prompt with context
                                           6. Generate answer (resilient Gemini call)
                                           7. Return answer + citations
```

**Grounding guarantee:** the system prompt instructs the model to answer **only** from the provided context and to say *"I don't have enough information"* when the answer isn't present - preventing hallucinations.

---

## 📁 Project Structure

```
src/main/java/rag_chatbot_application/
├── controller/
│   ├── IngestionController.java       # POST /api/ingest/pdf, /api/ingest/url
│   └── RagController.java             # POST /api/rag/ask, /api/rag/ask/stream
├── service/
│   ├── IngestionService.java
│   ├── RagService.java
│   ├── RetrievalService.java
│   ├── ResilientChatService.java
│   ├── VectorStoreService.java
│   ├── WebPageFetcher.java
│   ├── CitationMapper.java
│   └── impl/
│       ├── IngestionServiceImpl.java
│       ├── ManualRagService.java      # transparent, logged pipeline (@ConditionalOnProperty manual)
│       ├── AdvisorRagService.java     # idiomatic QuestionAnswerAdvisor (@ConditionalOnProperty advisor)
│       ├── RetrievalServiceImpl.java  # classification + fan-out + dedupe
│       ├── ResilientChatServiceImpl.java # fallback chain + retry/backoff
│       ├── VectorStoreServiceImpl.java
│       └── JsoupWebPageFetcher.java
├── model/                            # DTOs & records (RagAnswer, Citation, IngestResponse, ...)
├── exception/
│   ├── AllModelsExhaustedException.java
│   ├── DocumentIngestionException.java
│   ├── WebPageFetchException.java
│   └── handler/
│       ├── ApiError.java
│       └── GlobalExceptionHandler.java
├── health/
│   └── VectorStoreHealthIndicator.java
├── metrics/
│   └── RagMetrics.java
└── config/
    └── OpenApiConfig.java

src/main/resources/
├── application.properties            # shared config
├── application-dev.properties        # manual mode, verbose logs, Swagger on
├── application-prod.properties       # advisor mode, lean logs, Swagger off
└── static/
    └── index.html                    # clickable demo UI
```

---

## 🚀 Getting Started (Run It Locally)

### Prerequisites
- **Java 21+**
- **Maven 3.9+**
- **Docker** (easiest way to run PostgreSQL + pgvector)
- A **Google Gemini API key** - get one free at [Google AI Studio](https://aistudio.google.com/apikey)

### 1. Clone the repo
```bash
git clone https://github.com/<your-username>/documind.git
cd documind
```

### 2. Start PostgreSQL with pgvector
```bash
docker run --name rag-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=ragdb \
  -p 5432:5432 \
  -d pgvector/pgvector:pg16
```

Enable the extension (only needed once):
```bash
docker exec -it rag-postgres psql -U postgres -d ragdb -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### 3. Set environment variables
```bash
export GEMINI_API_KEY="your-gemini-api-key"
export DB_USERNAME="postgres"
export DB_PASSWORD="postgres"
# optional:
export DB_URL="jdbc:postgresql://localhost:5432/ragdb"
export SPRING_PROFILES_ACTIVE="dev"
```

> On Windows PowerShell: `$env:GEMINI_API_KEY="..."`

### 4. Run the app
```bash
./mvnw spring-boot:run
```
The app starts on **http://localhost:8081**.

### 5. Try it out 🎉
- **Demo UI:** http://localhost:8081/
- **Swagger UI:** http://localhost:8081/swagger-ui.html
- **Health:** http://localhost:8081/actuator/health

---

## ⚙️ Configuration Reference

All settings live in `application.properties` and per-profile files. Key options:

| Property | Default | Description |
|---|---|---|
| `spring.profiles.active` | `dev` | `dev` (manual, verbose) or `prod` (advisor, lean) |
| `rag.mode` | profile-driven | `manual` (transparent) or `advisor` (idiomatic) |
| `rag.retrieval.specific.top-k` | `6` | Chunks for specific queries |
| `rag.retrieval.specific.similarity-threshold` | `0.5` | Min similarity for specific queries |
| `rag.retrieval.broad.top-k` | `20` | Chunks for broad/summary queries |
| `rag.retrieval.broad.sub-queries` | `3` | Fan-out sub-query count |
| `rag.retrieval.fanout-enabled` | `true` | Toggle adaptive fan-out |
| `rag.models.fallback-chain` | `gemini-3.6-flash,gemini-3.6-flash-lite` | Ordered model fallback chain |
| `rag.resilience.max-retries` | `2` | Retries per model before falling back |
| `rag.resilience.backoff-ms` | `800` | Base backoff (exponential) |
| `rag.chunking.chunk-size` | `500` | Chunk token size |
| `rag.chunking.chunk-overlap` | `120` | Overlap between chunks |

> **Secrets** (`GEMINI_API_KEY`, DB credentials) are provided via environment variables and never committed.

---

## 📡 API Endpoints

### Ingestion

#### `POST /api/ingest/pdf` - Upload a PDF
```bash
curl -X POST http://localhost:8081/api/ingest/pdf \
  -F "file=@/path/to/document.pdf"
```
**Response:**
```json
{ "source": "document.pdf", "pages": 12, "chunksStored": 34 }
```

#### `POST /api/ingest/url` - Ingest a web page
```bash
curl -X POST http://localhost:8081/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{ "url": "https://en.wikipedia.org/wiki/Retrieval-augmented_generation" }'
```
**Response:**
```json
{ "source": "https://en.wikipedia.org/...", "pages": 1, "chunksStored": 21 }
```

### RAG Query

#### `POST /api/rag/ask` - Ask a question (with citations)
```bash
curl -X POST http://localhost:8081/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{ "question": "What is retrieval-augmented generation?" }'
```
**Response:**
```json
{
  "answer": "Retrieval-augmented generation (RAG) combines...",
  "citations": [
    {
      "source": "https://en.wikipedia.org/...",
      "title": "Retrieval-augmented generation",
      "snippet": "RAG combines an information retrieval component...",
      "score": 0.87
    }
  ]
}
```

#### `POST /api/rag/ask/stream` - Streaming answer (SSE)
```bash
curl -N -X POST http://localhost:8081/api/rag/ask/stream \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{ "question": "Summarize the document" }'
```
Streams answer tokens as Server-Sent Events.

### Error responses (consistent shape)
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "All configured models are unavailable or rate-limited. Please try again later.",
  "path": "/api/rag/ask",
  "timestamp": "2025-01-01T12:00:00Z"
}
```

| Status | When |
|---|---|
| `400` | Validation failure / bad input / non-PDF file |
| `404` | URL page not found |
| `413` | Uploaded file too large |
| `422` | URL unfetchable / too little readable text |
| `429` | All fallback models exhausted (rate-limited) |
| `500` | Unexpected error (no stack trace leaked) |

---

## 📊 Health & Monitoring

Spring Boot Actuator exposes operational endpoints:

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Overall health incl. **custom `vectorStore`** indicator (DB + pgvector reachability) |
| `GET /actuator/info` | Build/app info |
| `GET /actuator/metrics` | All metrics (incl. custom counters) |
| `GET /actuator/metrics/rag.queries` | # of RAG questions answered |
| `GET /actuator/metrics/rag.ingested` | # of documents ingested |
| `GET /actuator/metrics/rag.fallbacks` | # of model-fallback activations |
| `GET /actuator/prometheus` | Prometheus-format metrics (for scraping) |

**Custom health check example:**
```json
{
  "status": "UP",
  "components": {
    "vectorStore": {
      "status": "UP",
      "details": { "database": "reachable", "pgvector": "installed" }
    }
  }
}
```

> ⚠️ **Note on metrics persistence:** Micrometer counters are **in-memory** and reset on restart *by design*. For persistent, historical dashboards, scrape `/actuator/prometheus` with **Prometheus** and visualize in **Grafana** (dependency already included).

---

## 🛡 Resilience: The Model Fallback Chain

The headline reliability feature. When the primary model is rate-limited (HTTP 429 → `TransientAiException`) or fails, the app **automatically rotates to the next model** in the configured chain - no downtime, no user-facing error:

```
gemini-3.6-flash  ──(429?)──►  gemini-3.6-flash-lite  ──(429?)──►  clean 429 to client
        │                              │
    retry×N w/ backoff           retry×N w/ backoff
```

- Configured via `rag.models.fallback-chain` (ordered, comma-separated).
- Per-model **exponential-backoff retries** for transient blips.
- **Fail-fast** on non-transient errors (e.g. bad API key → `NonTransientAiException`).
- When *all* models are exhausted → a clean **429** with a retry hint (never a 500/stack trace).

---

## 🎯 Adaptive Retrieval & Query Fan-out

Not all questions need the same retrieval strategy:

| Query type | Example | Strategy |
|---|---|---|
| **Specific** | *"What port does the app run on?"* | Single focused search, threshold on → precision |
| **Broad** | *"Summarize the whole document"* | **Fan-out** into multiple sub-queries, wide net → coverage |

Broad queries are detected via keyword heuristics (*summarize, overview, key points…*), expanded into sub-queries, retrieved in parallel, then **merged and deduplicated** - dramatically improving summary-style answers. Toggle with `rag.retrieval.fanout-enabled`.

---

## 🧪 Running Tests

```bash
./mvnw test
```

Includes unit tests for:
- **Retrieval** - classification, fan-out, deduplication
- **Resilience** - fallback engagement, retry-then-succeed, all-exhausted
- **Health** - vector store UP/DOWN reporting

---

## 🗺 Roadmap / Future Enhancements

- [ ] **Multi-provider support** - pluggable chat providers (Ollama, OpenAI) via config

[//]: # (- [ ] **Prometheus + Grafana dashboard** - persistent metrics visualization)

[//]: # (- [ ] **Containerization** - `Dockerfile` + `docker-compose` &#40;app + pgvector&#41;)

[//]: # (- [ ] **CI/CD** - GitHub Actions build/test pipeline)
[//]: # (- [ ] **API security** - API-key/JWT auth layer)
[//]: # (- [ ] **Conversation memory** - multi-turn chat history)
[//]: # (- [ ] **Reranking** - cross-encoder reranking for retrieval quality)

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with ☕ Java, 🍃 Spring Boot, and 🤖 Spring AI.<br/>
  <em>If you find this project useful, please ⭐ the repo!</em>
</p>