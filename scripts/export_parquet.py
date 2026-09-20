import os
import psycopg2
import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
import duckdb

# ── Connect ───────────────────────────────────────────────────────────────────
url = os.environ["DATABASE_URL"]

# psycopg2 needs postgresql:// not jdbc:postgresql://
conn_str = url.replace("jdbc:postgresql://", "postgresql://")

conn = psycopg2.connect(conn_str)

# ── Fetch data ────────────────────────────────────────────────────────────────
query = """
    SELECT
        e.id               AS evaluation_id,
        er.id              AS run_id,
        q.query_text,
        p.name             AS product_name,
        p.category,
        p.price,
        sr.rank_position,
        sr.retrieval_score,
        e.relevance_score,
        e.llm_reasoning,
        e.evaluated_at,
        er.model_used,
        er.total_latency_ms
    FROM evaluations e
    JOIN search_results   sr ON sr.id       = e.search_result_id
    JOIN evaluation_runs  er ON er.id       = sr.run_id
    JOIN queries          q  ON q.id        = er.query_id
    JOIN products         p  ON p.id        = sr.product_id
    WHERE er.status = 'complete'
    ORDER BY e.evaluated_at DESC
"""

print("Fetching evaluations from Neon...")
df = pd.read_sql(query, conn)
conn.close()

print(f"Fetched {len(df)} rows")
print(df.head())

# ── Write Parquet ─────────────────────────────────────────────────────────────
output_path = "scripts/evaluations.parquet"

table = pa.Table.from_pandas(df)
pq.write_table(table, output_path, compression="snappy")

print(f"\n✅ Written to {output_path}")
print(f"   File size: {os.path.getsize(output_path):,} bytes")
print(f"   Schema:\n{table.schema}")

# ── Query with DuckDB ─────────────────────────────────────────────────────────
print("\n── DuckDB Analysis ──────────────────────────────────────────────")

duck = duckdb.connect()

print("\nAvg relevance score by category:")
print(duck.execute("""
    SELECT
        category,
        ROUND(AVG(relevance_score), 2) AS avg_score,
        COUNT(*)                        AS total_evals
    FROM read_parquet('scripts/evaluations.parquet')
    GROUP BY category
    ORDER BY avg_score DESC
""").df().to_string(index=False))

print("\nWorst performing queries (avg score < 2):")
print(duck.execute("""
    SELECT
        query_text,
        ROUND(AVG(relevance_score), 2) AS avg_score,
        COUNT(*)                        AS results_evaluated
    FROM read_parquet('scripts/evaluations.parquet')
    GROUP BY query_text
    HAVING AVG(relevance_score) < 2
    ORDER BY avg_score ASC
""").df().to_string(index=False))

print("\nScore distribution:")
print(duck.execute("""
    SELECT
        relevance_score,
        COUNT(*) AS count,
        ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 1) AS pct
    FROM read_parquet('scripts/evaluations.parquet')
    GROUP BY relevance_score
    ORDER BY relevance_score DESC
""").df().to_string(index=False))

duck.close()
print("\n✅ Parquet export and analysis complete")
