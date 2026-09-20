package com.searchlens.model;

public record Product(
    int    id,
    String name,
    String category,
    String subcategory,
    String description,
    double price,
    String[] tags,
    double retrievalScore   // BM25 score, populated at search time
) {}
