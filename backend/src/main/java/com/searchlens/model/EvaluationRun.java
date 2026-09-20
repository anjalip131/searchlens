package com.searchlens.model;

import java.util.List;

public record EvaluationRun(
    int                   runId,
    int                   queryId,
    String                queryText,
    String                status,
    double                avgScore,
    int                   totalLatencyMs,
    List<EvaluationResult> results
) {}
