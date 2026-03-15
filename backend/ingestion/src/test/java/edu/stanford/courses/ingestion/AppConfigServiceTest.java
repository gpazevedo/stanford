package edu.stanford.courses.ingestion;

import edu.stanford.courses.ingestion.model.AppSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isA;

@ExtendWith(MockitoExtension.class)
class AppConfigServiceTest {

    @Mock AppConfigDataClient appConfigClient;
    AppConfigService service;

    @BeforeEach void setUp() {
        service = new AppConfigService(appConfigClient, "stanford-courses", "prod", "settings");
    }

    @Test
    void parsesSettingsFromJson() {
        when(appConfigClient.startConfigurationSession(isA(StartConfigurationSessionRequest.class))).thenReturn(
            StartConfigurationSessionResponse.builder()
                .initialConfigurationToken("token-1").build());
        when(appConfigClient.getLatestConfiguration(isA(GetLatestConfigurationRequest.class))).thenReturn(
            GetLatestConfigurationResponse.builder()
                .configuration(SdkBytes.fromUtf8String("""
                    {"embeddingModelId":"amazon.titan-embed-text-v2:0",
                     "generativeModelId":"anthropic.claude-sonnet-4-5",
                     "maxSearchResults":10,
                     "enableSemanticReranking":false,
                     "newPrereqEnforcement":true}
                    """))
                .nextPollConfigurationToken("token-2").build());

        AppSettings s = service.getSettings();

        assertThat(s.embeddingModelId()).isEqualTo("amazon.titan-embed-text-v2:0");
        assertThat(s.maxSearchResults()).isEqualTo(10);
        assertThat(s.newPrereqEnforcement()).isTrue();
    }
}
