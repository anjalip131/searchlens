package com.searchlens.evaluator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchlens.model.EvaluationResult;
import com.searchlens.model.Product;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EvaluatorService {

    private static final String MODEL       = "gemini-3.6-flash";
    private static final int    MAX_RETRIES = 3;

    private final OkHttpClient http   = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<EvaluationResult> evaluate(String query, List<Product> products) throws Exception {
        String prompt      = buildPrompt(query, products);
        String responseJson = callGeminiWithRetry(prompt);
        return parseScores(responseJson, products);
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(String query, List<Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a search relevance judge for an e-commerce platform.\n\n");
        sb.append("Query: \"").append(query).append("\"\n\n");
        sb.append("Results (in rank order):\n");

        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append(i + 1).append(". ").append(p.name())
              .append(" — ").append(p.description() != null ? p.description() : "").append("\n");
        }

        sb.append("""

            For each result, output a JSON array:
            [
              {"rank": 1, "relevance_score": 0, "reasoning": "one sentence"},
              ...
            ]

            Scoring rubric:
            3 = Perfectly relevant
            2 = Mostly relevant
            1 = Tangentially relevant
            0 = Not relevant

            Return ONLY the JSON array, no preamble, no markdown fences.
            """);

        return sb.toString();
    }

    // ── Gemini API call with retry + backoff ──────────────────────────────────

    private String callGeminiWithRetry(String prompt) throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null) throw new RuntimeException("GEMINI_API_KEY env var not set");

        String url  = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + apiKey;

        String body = mapper.writeValueAsString(new GeminiRequest(
            List.of(new GeminiContent(List.of(new GeminiPart(prompt))))
        ));

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode json = mapper.readTree(response.body().string());
                    return json.at("/candidates/0/content/parts/0/text").asText();
                }
                if (response.code() == 429 || response.code() >= 500) {
                    long wait = (long) Math.pow(2, attempt) * 1000;
                    System.err.printf("Gemini API %d — retrying in %dms%n", response.code(), wait);
                    Thread.sleep(wait);
                } else {
                    throw new RuntimeException("Gemini API error: " + response.code()
                        + " " + response.body().string());
                }
            } catch (IOException e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep((long) Math.pow(2, attempt) * 1000);
            }
        }
        throw new RuntimeException("Gemini API failed after " + MAX_RETRIES + " retries");
    }

    // ── Parse LLM response ────────────────────────────────────────────────────

    private List<EvaluationResult> parseScores(String json, List<Product> products) throws Exception {
        // Strip markdown fences if Gemini adds them anyway
        String clean = json.strip()
                           .replaceAll("^```json\\s*", "")
                           .replaceAll("^```\\s*", "")
                           .replaceAll("```$", "")
                           .strip();

        JsonNode array = mapper.readTree(clean);
        List<EvaluationResult> results = new ArrayList<>();

        for (JsonNode node : array) {
            int    rank      = node.get("rank").asInt();
            int    score     = node.get("relevance_score").asInt();
            String reasoning = node.get("reasoning").asText();
            Product p        = products.get(rank - 1);
            results.add(new EvaluationResult(rank, p.id(), p.name(), score, reasoning));
        }
        return results;
    }

    // ── Inner record types for JSON serialisation ─────────────────────────────

    record GeminiRequest(List<GeminiContent> contents) {}
    record GeminiContent(List<GeminiPart> parts) {}
    record GeminiPart(String text) {}
}
