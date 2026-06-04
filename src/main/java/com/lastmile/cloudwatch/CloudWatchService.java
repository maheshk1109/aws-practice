package com.lastmile.cloudwatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

/**
 * =============================================================================
 * 2.15 — CloudWatch: Alarms, Log Groups, Dashboards
 * =============================================================================
 *
 * WHAT IS CLOUDWATCH?
 * CloudWatch is AWS's monitoring and observability service.
 * It covers three main areas:
 *
 *  1. METRICS
 *     Numeric data points over time.
 *     Examples: SQS queue depth, Lambda error count, ECS CPU usage.
 *     AWS services publish their own metrics automatically.
 *     You can also publish your own custom metrics (see publishCustomMetric below).
 *
 *  2. LOGS
 *     Text log output from your applications.
 *     Lambda automatically sends logs to CloudWatch Log Groups.
 *     ECS Fargate sends logs if you configure the awslogs log driver.
 *     Structure: Log Group → Log Streams → Log Events
 *       Log Group  = one per application/function (e.g. /aws/lambda/order-processor)
 *       Log Stream = one per Lambda instance or ECS task
 *       Log Event  = one line of log output
 *
 *  3. ALARMS
 *     Watch a metric and trigger an action when it crosses a threshold.
 *     Action can be: send SNS notification (email/SMS), auto scale, stop EC2.
 *     Example: alarm fires when DLQ has >= 1 message → email sent to ops team.
 *
 * DASHBOARDS:
 *     Visual display of multiple metrics in one view.
 *     Created in AWS Console or via CloudFormation.
 *     Useful for: ops team monitoring, service health at a glance.
 * =============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudWatchService {

    // CloudWatchClient is auto-configured by Spring Cloud AWS
    // It picks up region from application.properties
    private final CloudWatchClient cloudWatchClient;

    // ── ALARM 1: SQS DLQ depth ───────────────────────────────────────────────
    // Fires when first message lands in the DLQ.
    // This means a message failed all retries — needs immediate attention.
    // snsAlertArn = ARN of an SNS topic that emails/SMSes the ops team
    public void createDlqAlarm(String dlqName, String snsAlertArn) {
        cloudWatchClient.putMetricAlarm(r -> r
                .alarmName(dlqName + "-depth-alarm")
                .alarmDescription("Fires when any message lands in DLQ: " + dlqName)
                // AWS/SQS is the namespace for built-in SQS metrics
                .namespace("AWS/SQS")
                // ApproximateNumberOfMessagesVisible = messages waiting to be consumed
                .metricName("ApproximateNumberOfMessagesVisible")
                .dimensions(Dimension.builder()
                        .name("QueueName")
                        .value(dlqName)
                        .build())
                .statistic(Statistic.SUM)
                .period(60)               // check every 60 seconds
                .evaluationPeriods(1)     // alarm if threshold breached for 1 period
                .threshold(1.0)           // threshold = 1 message (alarm on first message)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                // notBreaching = if no data in period, treat as OK (don't alarm on missing data)
                .treatMissingData("notBreaching")
                .alarmActions(snsAlertArn)   // notify this SNS topic when alarm fires
                .okActions(snsAlertArn));    // also notify when DLQ drains back to 0

        log.info("Created DLQ depth alarm for queue: {}", dlqName);
    }

    // ── ALARM 2: Lambda error rate ────────────────────────────────────────────
    // Fires when Lambda throws any error.
    // Useful for catching Lambda failures before they pile up.
    public void createLambdaErrorAlarm(String functionName, String snsAlertArn) {
        cloudWatchClient.putMetricAlarm(r -> r
                .alarmName(functionName + "-error-alarm")
                .alarmDescription("Lambda " + functionName + " is throwing errors")
                .namespace("AWS/Lambda")
                .metricName("Errors")   // built-in Lambda metric — counts thrown exceptions
                .dimensions(Dimension.builder()
                        .name("FunctionName")
                        .value(functionName)
                        .build())
                .statistic(Statistic.SUM)
                .period(60)
                .evaluationPeriods(1)
                .threshold(1.0)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD)
                .treatMissingData("notBreaching")
                .alarmActions(snsAlertArn));

        log.info("Created Lambda error alarm for function: {}", functionName);
    }

    // ── CUSTOM METRIC ─────────────────────────────────────────────────────────
    // AWS does not know how many orders your app processed — you must publish that yourself.
    // Custom metrics appear in CloudWatch under your chosen namespace.
    // You can then create alarms on them or display them on dashboards.
    public void publishOrdersProcessedMetric(String queueName, double count) {
        cloudWatchClient.putMetricData(r -> r
                // LastMile/Orders is your custom namespace — choose any name
                .namespace("LastMile/Orders")
                .metricData(MetricDatum.builder()
                        .metricName("OrdersProcessed")
                        .value(count)
                        .unit(StandardUnit.COUNT)
                        // dimension lets you filter/group the metric in dashboards
                        .dimensions(Dimension.builder()
                                .name("QueueName")
                                .value(queueName)
                                .build())
                        .build()));

        log.info("Published OrdersProcessed metric: count={} queue={}", count, queueName);
    }
}
