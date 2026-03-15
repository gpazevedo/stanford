package edu.stanford.courses.api.config;

public record AppSettings(
    String embeddingModelId,
    String generativeModelId,
    int    maxSearchResults,
    boolean enableSemanticReranking,
    boolean newPrereqEnforcement
) {}
