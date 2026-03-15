package edu.stanford.courses.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;

@Configuration
public class AwsConfig {
    @Bean DynamoDbClient dynamoDbClient()       { return DynamoDbClient.create(); }
    @Bean S3VectorsClient s3VectorsClient()     { return S3VectorsClient.create(); }
    @Bean BedrockRuntimeClient bedrockClient()  { return BedrockRuntimeClient.create(); }
    @Bean AppConfigDataClient appConfigClient() { return AppConfigDataClient.create(); }
}
