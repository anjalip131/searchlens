package com.searchlens.api;

import com.searchlens.db.DatabasePool;
import com.searchlens.evaluator.EvaluatorService;
import com.searchlens.model.EvaluationResult;
import com.searchlens.model.Product;
import com.searchlens.search.SearchService;
import io.javalin.http.Context;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EvaluationController {

    private final SearchService    search    = new SearchService();
    private final EvaluatorService evaluator = new EvaluatorService();

    // POST /api/evaluate  { "query": "...", "categoryHint": "..." }
    public void evaluate(Context ctx) throws Exception {
        String query        = ctx.bodyAsClass(Map.class).get("query").toString();
        Object hint         = ctx.bodyAsClass(Map.class).get("categoryHint");
        String categoryHint = hint != null ? hint.toString() : null;

        long start = System.currentTimeMillis();

        // 1. Persist query
        int queryId = insertQuery(query, categoryHint);

        // 2. Create evaluation run
        int runId = insertRun(queryId);

        try {
            // 3. Search
            List<Product> products = search.search(query, categoryHint);
            if (products.isEmpty()) {
                updateRunStatus(runId, "failed", "No products found for query");
                ctx.status(404).json(Map.of("error", "No products found"));
                return;
            }

            // 4. Persist search results
            insertSearchResults(runId, products);

            // 5. Evaluate with LLM
            List<EvaluationResult> results = evaluator.evaluate(query, products);

            // 6. Persist evaluations
            insertEvaluations(runId, results);

            // 7. Mark complete
            int latency = (int) (System.currentTimeMillis() - start);
            updateRunComplete(runId, latency);

            ctx.json(Map.of(
                "runId",   runId,
                "queryId", queryId,
                "query",   query,
                "latencyMs", latency,
                "results", results
            ));

        } catch (Exception e) {
            updateRunStatus(runId, "failed", e.getMessage());
            throw e;
        }
    }

    // GET /api/runs — history page
    public void listRuns(Context ctx) throws Exception {
        String sql = """
            SELECT er.id, q.query_text, er.status, er.started_at, er.total_latency_ms,
                   ROUND(AVG(e.relevance_score)::numeric, 2) AS avg_score,
                   COUNT(e.id) AS total_evaluated
            FROM evaluation_runs er
            JOIN queries q        ON q.id = er.query_id
            LEFT JOIN search_results sr ON sr.run_id = er.id
            LEFT JOIN evaluations e     ON e.search_result_id = sr.id
            GROUP BY er.id, q.query_text, er.status, er.started_at, er.total_latency_ms
            ORDER BY er.started_at DESC
            LIMIT 50
            """;

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabasePool.get().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(Map.of(
                    "runId",          rs.getInt("id"),
                    "queryText",      rs.getString("query_text"),
                    "status",         rs.getString("status"),
                    "startedAt",      rs.getTimestamp("started_at").toString(),
                    "latencyMs",      rs.getObject("total_latency_ms") != null ? rs.getInt("total_latency_ms") : 0,
                    "avgScore",       rs.getObject("avg_score") != null ? rs.getDouble("avg_score") : 0.0,
                    "totalEvaluated", rs.getInt("total_evaluated")
                ));
            }
        }
        ctx.json(rows);
    }

    // GET /api/trends — trends page
    public void trends(Context ctx) throws Exception {
        String sql = """
            SELECT p.category,
                   DATE_TRUNC('day', er.started_at) AS day,
                   ROUND(AVG(e.relevance_score)::numeric, 2) AS avg_score,
                   COUNT(e.id) AS total_evals
            FROM evaluations e
            JOIN search_results  sr ON sr.id  = e.search_result_id
            JOIN evaluation_runs er ON er.id  = sr.run_id
            JOIN products        p  ON p.id   = sr.product_id
            WHERE er.status = 'complete'
            GROUP BY p.category, DATE_TRUNC('day', er.started_at)
            ORDER BY day DESC, avg_score DESC
            """;

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabasePool.get().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(Map.of(
                    "category",   rs.getString("category"),
                    "day",        rs.getTimestamp("day").toString(),
                    "avgScore",   rs.getDouble("avg_score"),
                    "totalEvals", rs.getInt("total_evals")
                ));
            }
        }
        ctx.json(rows);
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private int insertQuery(String queryText, String categoryHint) throws SQLException {
        String sql = "INSERT INTO queries(query_text, category_hint) VALUES(?,?) RETURNING id";
        try (Connection conn = DatabasePool.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queryText);
            ps.setString(2, categoryHint);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private int insertRun(int queryId) throws SQLException {
        String sql = "INSERT INTO evaluation_runs(query_id, status) VALUES(?, 'running') RETURNING id";
        try (Connection conn = DatabasePool.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, queryId);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private void insertSearchResults(int runId, List<Product> products) throws SQLException {
        String sql = "INSERT INTO search_results(run_id, product_id, rank_position, retrieval_score) VALUES(?,?,?,?)";
        try (Connection conn = DatabasePool.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < products.size(); i++) {
                ps.setInt(1, runId);
                ps.setInt(2, products.get(i).id());
                ps.setInt(3, i + 1);
                ps.setDouble(4, products.get(i).retrievalScore());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertEvaluations(int runId, List<EvaluationResult> results) throws SQLException {
        String sqlFetch = "SELECT id FROM search_results WHERE run_id=? AND rank_position=?";
        String sqlInsert = "INSERT INTO evaluations(search_result_id, relevance_score, llm_reasoning) VALUES(?,?,?)";

        try (Connection conn = DatabasePool.get().getConnection()) {
            for (EvaluationResult r : results) {
                int srId;
                try (PreparedStatement ps = conn.prepareStatement(sqlFetch)) {
                    ps.setInt(1, runId);
                    ps.setInt(2, r.rank());
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    srId = rs.getInt(1);
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setInt(1, srId);
                    ps.setInt(2, r.relevanceScore());
                    ps.setString(3, r.reasoning());
                    ps.executeUpdate();
                }
            }
        }
    }

    private void updateRunStatus(int runId, String status, String error) throws SQLException {
        String sql = "UPDATE evaluation_runs SET status=?, error_message=?, completed_at=NOW() WHERE id=?";
        try (Connection conn = DatabasePool.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, error);
            ps.setInt(3, runId);
            ps.executeUpdate();
        }
    }

    private void updateRunComplete(int runId, int latencyMs) throws SQLException {
        String sql = "UPDATE evaluation_runs SET status='complete', completed_at=NOW(), total_latency_ms=? WHERE id=?";
        try (Connection conn = DatabasePool.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, latencyMs);
            ps.setInt(2, runId);
            ps.executeUpdate();
        }
    }
}
