package edu.stanford.courses.api.applications.domain.models;
public record Application(String userId, String courseId, String status,
                          String appliedAt, String updatedAt) {}
