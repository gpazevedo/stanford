package edu.stanford.courses.ingestion.model;

public record AppSettings(
    String embeddingModelId,
    String generativeModelId,
    int    maxSearchResults,
    boolean enableSemanticReranking,
    boolean newPrereqEnforcement
) {}
