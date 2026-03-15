package edu.stanford.courses.api.courses.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.stanford.courses.api.config.AppConfigService;
import edu.stanford.courses.api.courses.domain.models.Course;
import edu.stanford.courses.api.courses.rest.dtos.CourseDetailResponse;
import edu.stanford.courses.api.courses.rest.dtos.CourseSearchResponse;
import edu.stanford.courses.api.applications.domain.ApplicationRepository;
import edu.stanford.courses.api.applications.domain.models.Application;
import edu.stanford.courses.api.users.domain.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseSearchService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final S3VectorsClient s3Vectors;
    private final BedrockRuntimeClient bedrock;
    private final CourseRepository courseRepo;
    private final ApplicationRepository appRepo;
    private final AppConfigService appConfig;
    private final String vectorIndex;

    public CourseSearchService(S3VectorsClient s3Vectors, BedrockRuntimeClient bedrock,
                               CourseRepository courseRepo, ApplicationRepository appRepo,
                               AppConfigService appConfig,
                               @Value("${aws.vectors.index}") String vectorIndex) {
        this.s3Vectors = s3Vectors; this.bedrock = bedrock;
        this.courseRepo = courseRepo; this.appRepo = appRepo;
        this.appConfig = appConfig; this.vectorIndex = vectorIndex;
    }

    public List<CourseSearchResponse> search(String query, int limit, String userId, User user) {
        int clampedLimit = Math.min(limit, 20);
        var modelId = appConfig.getSettings().embeddingModelId();
        var embedding = embed(query, modelId);

        var vectorResults = queryVectors(embedding, clampedLimit);

        var ids = vectorResults.stream().map(QueryOutputVector::key).collect(Collectors.toSet());
        var courses = courseRepo.findAllByIds(ids);
        var appliedIds = userId != null
            ? appRepo.findAppliedByUser(userId).stream().map(Application::courseId).collect(Collectors.toSet())
            : Collections.<String>emptySet();

        return vectorResults.stream()
            .map(r -> courses.get(r.key()))
            .filter(Objects::nonNull)
            .map(c -> toSearchResponse(c, user, appliedIds))
            .toList();
    }

    public CourseDetailResponse getById(String courseId, String userId, User user) {
        var course = courseRepo.findById(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var applied = appRepo.findByUserAndCourse(userId, courseId)
            .map(a -> "APPLIED".equals(a.status())).orElse(false);
        var prereqs = course.prerequisites().stream().map(prereqId -> {
            var prereqCourse = courseRepo.findById(prereqId);
            var completed = user != null ? user.completedCourseIds() : List.<String>of();
            return new CourseDetailResponse.PrereqStatus(
                prereqId,
                prereqCourse.map(Course::title).orElse(prereqId),
                completed.contains(prereqId));
        }).toList();
        var canApply = prereqs.stream().allMatch(CourseDetailResponse.PrereqStatus::met);
        return new CourseDetailResponse(course.courseId(), course.title(), course.description(),
            course.units(), course.quarter(), course.instructors(), course.prereqNote(),
            prereqs, canApply, applied);
    }

    private List<QueryOutputVector> queryVectors(float[] embedding, int topK) {
        var floatList = new ArrayList<Float>(embedding.length);
        for (float v : embedding) floatList.add(v);
        return s3Vectors.queryVectors(QueryVectorsRequest.builder()
            .indexName(vectorIndex)
            .queryVector(VectorData.fromFloat32(floatList))
            .topK(topK)
            .build()).vectors();
    }

    private CourseSearchResponse toSearchResponse(Course c, User user, Set<String> appliedIds) {
        var completed = user != null ? user.completedCourseIds() : List.<String>of();
        var missing = c.prerequisites().stream()
            .filter(p -> !completed.contains(p)).toList();
        return new CourseSearchResponse(c.courseId(), c.title(), c.units(),
            missing.isEmpty(), missing, appliedIds.contains(c.courseId()));
    }

    private float[] embed(String text, String modelId) {
        var payload = MAPPER.createObjectNode().put("inputText", text).toString();
        var resp = bedrock.invokeModel(InvokeModelRequest.builder()
            .modelId(modelId).contentType("application/json").accept("application/json")
            .body(SdkBytes.fromUtf8String(payload)).build());
        try {
            var arr = MAPPER.readTree(resp.body().asUtf8String()).get("embedding");
            var vec = new float[arr.size()];
            for (int i = 0; i < vec.length; i++) vec[i] = (float) arr.get(i).asDouble();
            return vec;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Bedrock embedding response", e);
        }
    }
}
