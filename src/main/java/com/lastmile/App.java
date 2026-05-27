package com.lastmile;

import com.lastmile.config.AwsConfig;

public class App {
    public static void main(String[] args) {
        AwsConfig config = new AwsConfig();

        System.out.println("DynamoDB Client: " + config.dynamoDbClient());
        System.out.println("SQS Client: " + config.sqsClient());
        System.out.println("SNS Client: " + config.snsClient());

        System.out.println("AWS setup verified");
    }
}