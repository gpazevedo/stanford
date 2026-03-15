package edu.stanford.courses.ingestion;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

public class IngestionHandler implements RequestHandler<ScheduledEvent, Void> {

    private final BulletinScraper scraper;
    private final IngestionService service;

    /** Production constructor — all clients initialized once (cold start). */
    public IngestionHandler() {
        var appConfig = new AppConfigService(
            AppConfigDataClient.create(),
            System.getenv("APPCONFIG_APP"),
            System.getenv("APPCONFIG_ENV"),
            System.getenv("APPCONFIG_PROFILE"));
        this.scraper = new BulletinScraper();
        this.service = new IngestionService(
            DynamoDbClient.create(),
            BedrockRuntimeClient.create(),
            S3VectorsClient.create(),
            appConfig,
            System.getenv("COURSES_TABLE"),
            System.getenv("APPLICATIONS_TABLE"),
            System.getenv("VECTOR_INDEX"));
    }

    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        try {
            service.ingest(scraper.scrape());
        } catch (Exception e) {
            throw new RuntimeException("Ingestion failed", e);
        }
        return null;
    }
}
