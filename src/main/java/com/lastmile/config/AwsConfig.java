package com.lastmile.config;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public class AwsConfig {

    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.AP_SOUTH_1)
                .build();
    }

    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.AP_SOUTH_1)
                .build();
    }

    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.AP_SOUTH_1)
                .build();
    }
}