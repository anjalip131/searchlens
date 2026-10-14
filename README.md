# SearchLens — AI-Powered Search Relevance Evaluator

Search relevance evaluation at e-commerce scale is manual, slow, and subjective.
Human raters take days to assess query-result quality. SearchLens automates this
using LLM-based scoring, giving engineers instant, explainable relevance signals.

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
