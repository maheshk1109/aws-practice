package com.lastmile.config;

public class AwsConfig {

    public void configureClients() {
        System.out.println("DynamoDB Client Configured");
        System.out.println("SQS Client Configured");
        System.out.println("SNS Client Configured");
    }
}
