import os
import json
import psycopg2
import psycopg2.extras
from mcp.server.fastmcp import FastMCP

# ── Setup ─────────────────────────────────────────────────────────────────────

mcp = FastMCP("SearchLens")

def get_conn():
    url = os.environ["DATABASE_URL"]
    return psycopg2.connect(url.replace("jdbc:postgresql://", "postgresql://"))

# ── Tools ─────────────────────────────────────────────────────────────────────

@mcp.tool()
def get_evaluation_summary(query_text: str) -> str:
    """
    Get the average relevance score and score distribution for a specific search query.
    Use this to understand how well the search engine performs for a given query.
    """
    sql = """
        SELECT
            q.query_text,
            ROUND(AVG(e.relevance_score)::numeric, 2) AS avg_score,
            COUNT(e.id)                                AS total_evals,
            SUM(CASE WHEN e.relevance_score = 3 THEN 1 ELSE 0 END) AS score_3,
            SUM(CASE WHEN e.relevance_score = 2 THEN 1 ELSE 0 END) AS score_2,
            SUM(CASE WHEN e.relevance_score = 1 THEN 1 ELSE 0 END) AS score_1,
            SUM(CASE WHEN e.relevance_score = 0 THEN 1 ELSE 0 END) AS score_0
        FROM evaluations e
        JOIN search_results  sr ON sr.id  = e.search_result_id
        JOIN evaluation_runs er ON er.id  = sr.run_id
        JOIN queries          q ON q.id   = er.query_id
        WHERE er.status = 'complete'
          AND LOWER(q.query_text) LIKE LOWER(%s)
        GROUP BY q.query_text
        ORDER BY avg_score ASC
    """
    with get_conn() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, (f"%{query_text}%",))
            rows = cur.fetchall()

    if not rows:
        return f"No evaluation data found for query: '{query_text}'"

    result = []
    for row in rows:
        result.append({
            "query":       row["query_text"],
            "avg_score":   float(row["avg_score"]),
            "total_evals": row["total_evals"],
            "distribution": {
                "perfect (3)":      row["score_3"],
                "mostly (2)":       row["score_2"],
                "tangential (1)":   row["score_1"],
                "irrelevant (0)":   row["score_0"],
            }
        })
    return json.dumps(result, indent=2)


@mcp.tool()
def list_worst_queries(limit: int = 5, min_evals: int = 1) -> str:
    """
    List the search queries with the lowest average relevance scores.
    Use this to identify which queries the search engine handles poorly.
    Args:
        limit: number of queries to return (default 5)
        min_evals: minimum number of evaluations required (default 1)
    """
    sql = """
        SELECT
            q.query_text,
            ROUND(AVG(e.relevance_score)::numeric, 2) AS avg_score,
            COUNT(e.id)                                AS total_evals,
            MAX(er.started_at)                         AS last_evaluated
        FROM evaluations e
        JOIN search_results  sr ON sr.id  = e.search_result_id
        JOIN evaluation_runs er ON er.id  = sr.run_id
        JOIN queries          q ON q.id   = er.query_id
        WHERE er.status = 'complete'
        GROUP BY q.query_text
        HAVING COUNT(e.id) >= %s
        ORDER BY avg_score ASC
        LIMIT %s
    """
    with get_conn() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, (min_evals, limit))
            rows = cur.fetchall()

    if not rows:
        return "No evaluation data found."

    result = []
    for row in rows:
        result.append({
            "query":          row["query_text"],
            "avg_score":      float(row["avg_score"]),
            "total_evals":    row["total_evals"],
            "last_evaluated": str(row["last_evaluated"]),
        })
    return json.dumps(result, indent=2)


@mcp.tool()
def explain_run(run_id: int) -> str:
    """
    Show the full LLM reasoning for every result in an evaluation run.
    Use this to understand WHY each product was scored the way it was.
    Args:
        run_id: the ID of the evaluation run to explain
    """
    sql = """
        SELECT
            q.query_text,
            p.name             AS product_name,
            p.category,
            sr.rank_position,
            e.relevance_score,
            e.llm_reasoning
        FROM evaluations e
        JOIN search_results  sr ON sr.id  = e.search_result_id
        JOIN evaluation_runs er ON er.id  = sr.run_id
        JOIN queries          q ON q.id   = er.query_id
        JOIN products         p ON p.id   = sr.product_id
        WHERE er.id = %s
        ORDER BY sr.rank_position
    """
    with get_conn() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, (run_id,))
            rows = cur.fetchall()

    if not rows:
        return f"No data found for run_id: {run_id}"

    result = {
        "run_id":     run_id,
        "query":      rows[0]["query_text"],
        "results": [
            {
                "rank":            row["rank_position"],
                "product":         row["product_name"],
                "category":        row["category"],
                "relevance_score": row["relevance_score"],
                "reasoning":       row["llm_reasoning"],
            }
            for row in rows
        ]
    }
    return json.dumps(result, indent=2)


@mcp.tool()
def list_recent_runs(limit: int = 10) -> str:
    """
    List the most recent evaluation runs with their status and scores.
    Use this to get an overview of recent search evaluation activity.
    Args:
        limit: number of runs to return (default 10)
    """
    sql = """
        SELECT
            er.id                                      AS run_id,
            q.query_text,
            er.status,
            er.started_at,
            er.total_latency_ms,
            ROUND(AVG(e.relevance_score)::numeric, 2)  AS avg_score,
            COUNT(e.id)                                AS total_evals
        FROM evaluation_runs er
        JOIN queries          q  ON q.id  = er.query_id
        LEFT JOIN search_results  sr ON sr.run_id = er.id
        LEFT JOIN evaluations     e  ON e.search_result_id = sr.id
        GROUP BY er.id, q.query_text, er.status, er.started_at, er.total_latency_ms
        ORDER BY er.started_at DESC
        LIMIT %s
    """
    with get_conn() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, (limit,))
            rows = cur.fetchall()

    result = [
        {
            "run_id":       row["run_id"],
            "query":        row["query_text"],
            "status":       row["status"],
            "avg_score":    float(row["avg_score"]) if row["avg_score"] else None,
            "total_evals":  row["total_evals"],
            "latency_ms":   row["total_latency_ms"],
            "started_at":   str(row["started_at"]),
        }
        for row in rows
    ]
    return json.dumps(result, indent=2)


# ── Run ───────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("✅ SearchLens MCP server starting...")
    mcp.run(transport="stdio")
