package edu.stanford.courses.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.stanford.courses.ingestion.model.AppSettings;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.*;
import java.time.Instant;

public class AppConfigService {

    private static final long TTL_SECONDS = 60;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AppConfigDataClient client;
    private final String applicationId, environmentId, configProfileId;
    private String configToken;
    private AppSettings cached;
    private Instant cacheExpiry = Instant.EPOCH;

    public AppConfigService(AppConfigDataClient client,
                            String applicationId,
                            String environmentId,
                            String configProfileId) {
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

        var response = client.getLatestConfiguration(
            GetLatestConfigurationRequest.builder()
                .configurationToken(configToken).build());
        configToken = response.nextPollConfigurationToken();

        var content = response.configuration().asUtf8String();
        if (!content.isBlank()) {
            try {
                cached = MAPPER.readValue(content, AppSettings.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse AppConfig settings", e);
            }
        }
        if (cached == null) {
            throw new RuntimeException("AppConfig returned empty configuration on first fetch");
        }
        cacheExpiry = Instant.now().plusSeconds(TTL_SECONDS);
        return cached;
    }
}
