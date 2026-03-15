package edu.stanford.courses.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.*;
import java.time.Instant;

@Service
public class AppConfigService {

    private static final long TTL_SECONDS = 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfigDataClient client;
    private final String applicationId, environmentId, configProfileId;
    private String configToken;
    private AppSettings cached;
    private Instant cacheExpiry = Instant.EPOCH;

    public AppConfigService(AppConfigDataClient client,
                            @Value("${aws.appconfig.app}")     String applicationId,
                            @Value("${aws.appconfig.env}")     String environmentId,
                            @Value("${aws.appconfig.profile}") String configProfileId) {
        this.client = client;
        this.applicationId = applicationId;
        this.environmentId = environmentId;
        this.configProfileId = configProfileId;
    }

    public synchronized AppSettings getSettings() {
        if (cached != null && Instant.now().isBefore(cacheExpiry)) return cached;

        if (configToken == null) {
            configToken = client.startConfigurationSession(
                StartConfigurationSessionRequest.builder()
                    .applicationIdentifier(applicationId)
                    .environmentIdentifier(environmentId)
                    .configurationProfileIdentifier(configProfileId)
                    .build()
            ).initialConfigurationToken();
        }

        GetLatestConfigurationResponse resp;
        try {
            resp = client.getLatestConfiguration(
                GetLatestConfigurationRequest.builder().configurationToken(configToken).build());
        } catch (software.amazon.awssdk.services.appconfigdata.model.ResourceNotFoundException |
                 software.amazon.awssdk.services.appconfigdata.model.BadRequestException e) {
            // Stale token — start a new session and retry once
            configToken = client.startConfigurationSession(
                StartConfigurationSessionRequest.builder()
                    .applicationIdentifier(applicationId)
                    .environmentIdentifier(environmentId)
                    .configurationProfileIdentifier(configProfileId)
                    .build()
            ).initialConfigurationToken();
            resp = client.getLatestConfiguration(
                GetLatestConfigurationRequest.builder().configurationToken(configToken).build());
        }
        configToken = resp.nextPollConfigurationToken();

        var content = resp.configuration().asUtf8String();
        if (!content.isBlank()) {
            try { cached = MAPPER.readValue(content, AppSettings.class); }
            catch (Exception e) { throw new RuntimeException("Failed to parse AppConfig", e); }
        }
        if (cached == null) {
            throw new RuntimeException("AppConfig returned empty configuration on first fetch");
        }
        var nextPoll = resp.nextPollIntervalInSeconds();
        cacheExpiry = Instant.now().plusSeconds(
            Math.max(TTL_SECONDS, nextPoll != null ? nextPoll : 0));
        return cached;
    }
}
