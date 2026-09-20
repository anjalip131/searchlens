# SearchLens — AI-Powered Search Relevance Evaluator

Search relevance evaluation at e-commerce scale is manual, slow, and subjective.
Human raters take days to assess query-result quality. SearchLens automates this
using LLM-based scoring, giving engineers instant, explainable relevance signals.

Built from domain experience working on search ranking and CTR optimization at Flipkart.

---

## Architecture
[Next.js Frontend] → [Java Backend :8080] → [PostgreSQL / Neon]
TypeScript Javalin + HikariCP 5 normalized tables
React Query BM25 full-text search
Recharts Gemini AI evaluation
↓
[Python MCP Server] [Parquet Export]
4 LLM-callable tools PyArrow + DuckDB
stdio transport Snappy compression
## ER Diagram
queries ──1:N──► evaluation_runs ──1:N──► search_results ──1:1──► evaluations
│
└──N:1──► products

---

## Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 15, TypeScript, React Query, Recharts, Tailwind |
| Backend | Java 21, Javalin, HikariCP, OkHttp |
| Database | PostgreSQL (Neon), 5-table normalized schema |
| AI | Gemini 3.6 Flash — 0–3 IR relevance scoring |
| Big Data | PyArrow, Parquet (Snappy), DuckDB |
| AI Tooling | MCP server (Python) with 4 tools |
| Dev | GitHub Codespaces, Maven |

---

## Key Design Decisions

**Why is `evaluation_runs` separate from `queries`?**
The same query can be re-evaluated with a different model or prompt version over time.
Separating runs from queries lets you compare scores across model versions — which is
exactly what a production eval harness needs.

**Why PostgreSQL full-text search with ILIKE fallback?**
`ts_rank` with `plainto_tsquery` handles stemming and ranking efficiently.
The ILIKE fallback catches multi-word queries where individual terms do not form
a valid tsquery — trading precision for recall at small catalog scale.
At production scale this would be replaced with Solr or OpenSearch.

**Why store `retrieval_score` on `search_results`?**
Enables correlation analysis between BM25 retrieval score and LLM relevance score.
Key question: does a high retrieval score predict high relevance? Early data suggests
it does for exact matches but not for broad queries.

**Why Parquet with Snappy compression for export?**
Snappy optimises for read speed over compression ratio — correct for analytics
workloads where repeated aggregation queries run over the same data.
At scale this would land in S3 and be queryable via Athena or Spark.

**Why MCP over a REST API for the LLM interface?**
MCP gives the LLM structured tool definitions with typed inputs and descriptions.
An agent can reason about which tool to call and why — enabling autonomous workflows
like detecting degrading query quality and triggering re-evaluation without human
intervention.

---

## What the data showed

Running evaluations across query categories revealed:

- **Books scored 1.5/3** — "productivity books" returned a System Design interview
  book (scored 0), exposing a catalog-query mismatch
- **Sports scored 2.67/3** — broad queries return category-correct but
  specificity-mismatched results
- **89.5% of results scored 3/3** — expected at small catalog scale; score
  distribution degrades meaningfully at 50k+ products

---

## MCP Server Tools

| Tool | Description |
|---|---|
| `get_evaluation_summary` | Avg score and distribution for a query |
| `list_worst_queries` | Queries ranked by lowest avg relevance score |
| `explain_run` | Full LLM reasoning for every result in a run |
| `list_recent_runs` | Overview of recent evaluation activity |

---

## What I would do at production scale

- Replace PostgreSQL FTS with **Apache Solr** (used in production at Flipkart)
- Partition `evaluations` table by month for query performance at scale
- Use **Apache Iceberg** instead of flat Parquet for ACID guarantees and time travel
- Add an **eval harness for the LLM prompt itself** to track how prompt changes affect scoring consistency
- Deploy backend on **Kubernetes** with HPA scaling on evaluator queue depth
- Add **Avro + Schema Registry** to the Parquet export pipeline
- Replace Gemini with a fine-tuned relevance model for lower latency and cost

---

## Setup

```bash
# 1. Backend
export DATABASE_URL="jdbc:postgresql://..."
export GEMINI_API_KEY="..."
cd backend && mvn package -q
java -jar target/searchlens-backend-1.0.0.jar

# 2. Frontend
cd frontend && npm install && npm run dev

# 3. MCP Server
cd mcp-server && python3 server.py

# 4. Parquet Export
python3 scripts/export_parquet.py
```
