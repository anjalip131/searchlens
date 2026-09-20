package com.searchlens.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public class DatabasePool {

    private static HikariDataSource ds;

    public static void init() {
        String url = System.getenv("DATABASE_URL");
        if (url == null) throw new RuntimeException("DATABASE_URL env var not set");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);

        ds = new HikariDataSource(config);
        System.out.println("✅ Database connected");
    }

    public static DataSource get() {
        if (ds == null) throw new RuntimeException("DatabasePool not initialised. Call init() first.");
        return ds;
    }
}
