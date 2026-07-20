package com.portfolio.csagent.service;

public record KnowledgeSearchResult(
        String citation,
        Long documentId,
        Long chunkId,
        String title,
        String sourceUri,
        String excerpt,
        double score,
        String dataSource) {
}
