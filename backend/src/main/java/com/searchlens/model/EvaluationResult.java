package com.searchlens.model;

public record EvaluationResult(
    int    rank,
    int    productId,
    String productName,
    int    relevanceScore,   // 0–3
    String reasoning
) {}
