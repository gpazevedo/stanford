package edu.stanford.courses.api.config;

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

@ExtendWith(MockitoExtension.class)
class AppConfigServiceTest {

    @Mock AppConfigDataClient client;

    @Test
    void returnsSettingsFromAppConfig() {
        when(client.startConfigurationSession(any(StartConfigurationSessionRequest.class))).thenReturn(
            StartConfigurationSessionResponse.builder()
                .initialConfigurationToken("tok-1").build());
        when(client.getLatestConfiguration(any(GetLatestConfigurationRequest.class))).thenReturn(
            GetLatestConfigurationResponse.builder()
                .configuration(SdkBytes.fromUtf8String("""
                    {"embeddingModelId":"amazon.titan-embed-text-v2:0",
                     "generativeModelId":"anthropic.claude-sonnet-4-5",
                     "maxSearchResults":15,
                     "enableSemanticReranking":false,
                     "newPrereqEnforcement":true}"""))
                .nextPollConfigurationToken("tok-2").build());

        var service = new AppConfigService(client, "app", "env", "profile");
        var settings = service.getSettings();

        assertThat(settings.embeddingModelId()).isEqualTo("amazon.titan-embed-text-v2:0");
        assertThat(settings.maxSearchResults()).isEqualTo(15);
    }
}
