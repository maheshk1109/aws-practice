package com.lastmile.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

/**
 * 2.3 vs 2.4 — Manual SDK setup vs Spring Cloud AWS Auto-configuration
 *
 * ── 2.3 MANUAL (what this class does) ────────────────────────────────────────
 * You build each AWS client yourself using AWS SDK v2 builder pattern.
 * You control region, endpoint, credentials explicitly.
 * Useful when you need fine-grained control or are NOT using Spring Boot.
 *
 * ── 2.4 SPRING CLOUD AWS AUTO-CONFIG (what you get for free) ─────────────────
 * Add spring-cloud-aws-starter to pom.xml.
 * Set spring.cloud.aws.region.static=ap-southeast-2 in application.properties.
 * Spring Boot auto-creates SqsAsyncClient, SnsClient, DynamoDbClient beans.
 * You just @Autowired inject SqsTemplate or SnsTemplate — no @Bean needed.
 *
 * ── CREDENTIAL CHAIN (both approaches use this) ───────────────────────────────
 * AWS SDK looks for credentials in this order:
 *  1. Environment variables     AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 *  2. System properties         aws.accessKeyId, aws.secretAccessKey
 *  3. ~/.aws/credentials file   (set by 'aws configure')
 *  4. IAM Role on EC2/ECS/Lambda (best for production — no keys needed)
 *
 * In production ALWAYS use option 4 — IAM role attached to the compute resource.
 * Never hardcode credentials in code or application.properties.
 */
@Configuration
public class AwsConfig {

    // aws.region is read from application.properties
    @Value("${aws.region:ap-southeast-2}")
    private String region;

    // aws.endpoint-override is optional — used for LocalStack local testing
    // Example: aws.endpoint-override=http://localhost:4566
    // In production this property is not set, so it defaults to null
    @Value("${aws.endpoint-override:#{null}}")
    private String endpointOverride;

    // ── DynamoDB Client ───────────────────────────────────────────────────────
    // DynamoDbClient is synchronous — good for standard request/response
    // DynamoDbAsyncClient is async — needed for reactive applications
    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region));

        // if endpoint override is set (e.g. LocalStack), use it
        // otherwise SDK uses the real AWS endpoint automatically
        if (endpointOverride != null) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    // ── DynamoDB Enhanced Client ──────────────────────────────────────────────
    // Wraps DynamoDbClient to map Java objects <-> DynamoDB items via @DynamoDbBean
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    // ── SQS Client ────────────────────────────────────────────────────────────
    // Spring Cloud AWS SQS starter needs SqsAsyncClient (not SqsClient)
    // because @SqsListener uses non-blocking polling internally
    @Bean
    public SqsClient sqsClient() {
        var builder = SqsClient.builder()
                .region(Region.of(region));

        if (endpointOverride != null) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    // ── SNS Client ────────────────────────────────────────────────────────────
    // Used by SnsTemplate to publish messages to topics
    @Bean
    public SnsClient snsClient() {
        var builder = SnsClient.builder()
                .region(Region.of(region));

        if (endpointOverride != null) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    // ── CloudWatch Client (2.18, 2.19) ──────────────────────────────────────────
    // Used by CloudWatchService to publish custom metrics and create alarms
    @Bean
    public CloudWatchClient cloudWatchClient() {
        var builder = CloudWatchClient.builder()
                .region(Region.of(region));

        if (endpointOverride != null) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    // ── Secrets Manager Client (2.21) ─────────────────────────────────────────
    // Used to fetch encrypted secrets: DB passwords, API keys, tokens
    // In production: IAM role must have secretsmanager:GetSecretValue
    @Bean
    public SecretsManagerClient secretsManagerClient() {
        var builder = SecretsManagerClient.builder()
                .region(Region.of(region));

        if (endpointOverride != null) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    // ── SSM Parameter Store Client (2.21) ─────────────────────────────────────
    // Used to fetch config values and SecureString params from Parameter Store
    // In production: IAM role must have ssm:GetParameter, ssm:GetParametersByPath
    @Bean
    public SsmClient ssmClient() {
        var builder = SsmClient.builder()
                .region(Region.of(region));

        if (endpointOverride != null) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }
}
