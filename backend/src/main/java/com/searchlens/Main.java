package com.searchlens;

import com.searchlens.api.EvaluationController;
import com.searchlens.db.DatabasePool;
import io.javalin.Javalin;

public class Main {

    public static void main(String[] args) {
        DatabasePool.init();

        EvaluationController controller = new EvaluationController();

        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
        });

        app.post("/api/evaluate",  controller::evaluate);
        app.get("/api/runs",       controller::listRuns);
        app.get("/api/trends",     controller::trends);
        app.get("/api/health",     ctx -> ctx.result("ok"));

        app.start(8080);
        System.out.println("✅ SearchLens backend running on :8080");
    }
}
