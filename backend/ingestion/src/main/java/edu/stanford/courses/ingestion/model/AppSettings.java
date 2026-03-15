package edu.stanford.courses.ingestion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppSettings(
    String embeddingModelId,
    String generativeModelId,
    int    maxSearchResults,
    boolean enableSemanticReranking,
    boolean newPrereqEnforcement
) {}
