package com.searchlens.search;

import com.searchlens.db.DatabasePool;
import com.searchlens.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SearchService {

    private static final int TOP_K = 10;

    public List<Product> search(String query, String categoryHint) throws SQLException {
        List<Product> results = fullTextSearch(query, categoryHint);
        if (results.isEmpty()) {
            results = likeSearch(query, categoryHint);
        }
        return results;
    }

    private List<Product> fullTextSearch(String query, String categoryHint) throws SQLException {
        String sql = """
            SELECT p.id, p.name, p.category, p.subcategory, p.description, p.price, p.tags,
                   ts_rank(
                       to_tsvector('english', p.name || ' ' || COALESCE(p.description,'') || ' ' || COALESCE(array_to_string(p.tags,' '),'')),
                       plainto_tsquery('english', ?)
                   ) AS retrieval_score
            FROM products p
            WHERE to_tsvector('english', p.name || ' ' || COALESCE(p.description,'') || ' ' || COALESCE(array_to_string(p.tags,' '),''))
                  @@ plainto_tsquery('english', ?)
            %s
            ORDER BY retrieval_score DESC
            LIMIT ?
            """.formatted(categoryHint != null ? "AND p.category = ?" : "");

        return runQuery(sql, query, categoryHint);
    }

    // Fallback: simple keyword match on name, description, category, tags
    private List<Product> likeSearch(String query, String categoryHint) throws SQLException {
        String[] terms = query.toLowerCase().split("\\s+");
        StringBuilder conditions = new StringBuilder();
        for (String term : terms) {
            if (!conditions.isEmpty()) conditions.append(" OR ");
            conditions.append("(LOWER(p.name) LIKE '%").append(term)
                      .append("%' OR LOWER(p.description) LIKE '%").append(term)
                      .append("%' OR LOWER(p.category) LIKE '%").append(term)
                      .append("%' OR LOWER(p.subcategory) LIKE '%").append(term)
                      .append("%' OR LOWER(array_to_string(p.tags,' ')) LIKE '%").append(term).append("%')");
        }

        String sql = """
            SELECT p.id, p.name, p.category, p.subcategory, p.description, p.price, p.tags,
                   0.1 AS retrieval_score
            FROM products p
            WHERE (%s)
            %s
            LIMIT %d
            """.formatted(
                conditions,
                categoryHint != null ? "AND p.category = '" + categoryHint + "'" : "",
                TOP_K
            );

        List<Product> results = new ArrayList<>();
        try (Connection conn = DatabasePool.get().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        }
        return results;
    }

    private List<Product> runQuery(String sql, String query, String categoryHint) throws SQLException {
        List<Product> results = new ArrayList<>();
        try (Connection conn = DatabasePool.get().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, query);
            ps.setString(2, query);
            if (categoryHint != null) {
                ps.setString(3, categoryHint);
                ps.setInt(4, TOP_K);
            } else {
                ps.setInt(3, TOP_K);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(mapRow(rs));
        }
        return results;
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Array tagsArray = rs.getArray("tags");
        String[] tags = tagsArray != null ? (String[]) tagsArray.getArray() : new String[0];
        return new Product(
            rs.getInt("id"), rs.getString("name"), rs.getString("category"),
            rs.getString("subcategory"), rs.getString("description"),
            rs.getDouble("price"), tags, rs.getDouble("retrieval_score")
        );
    }
}
