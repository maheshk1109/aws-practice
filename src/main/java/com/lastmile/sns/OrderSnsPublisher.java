package com.lastmile.sns;

import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * =============================================================================
 * 2.13 — SNS Message Envelope, Filter Policies, Publishing
 * 2.14 — SNS + SQS End-to-end Messaging Flow
 * =============================================================================
 *
 * HOW SnsTemplate WORKS:
 *  SnsTemplate is provided by Spring Cloud AWS.
 *  It is auto-configured when spring-cloud-aws-starter-sns is on the classpath.
 *  It serializes your Java object to JSON and publishes it to the SNS topic.
 *  You do NOT need to manually call the AWS SDK SNS client.
 *
 * MESSAGE ATTRIBUTES (headers):
 *  When you call .setHeader("eventType", "ORDER_CREATED"), Spring Cloud AWS
 *  converts this to an SNS MessageAttribute.
 *  SNS filter policies on subscriptions check these attributes.
 *  → notification-queue (filter: eventType=ORDER_CREATED) receives ORDER_CREATED only
 *  → analytics-queue (no filter) receives everything
 *
 * END-TO-END FLOW (2.14):
 *  1. Order service calls publishOrderCreated()
 *  2. SnsTemplate serializes OrderEvent to JSON
 *  3. SNS receives the message + message attributes
 *  4. SNS checks each subscription's filter policy
 *  5. notification-queue receives it (eventType matches)
 *  6. analytics-queue receives it (no filter)
 *  7. Lambda or @SqsListener on each queue processes independently
 * =============================================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSnsPublisher {

    // SnsTemplate is auto-configured by Spring Cloud AWS
    // It needs spring-cloud-aws-starter-sns in pom.xml
    // and spring.cloud.aws.region.static in application.properties
    private final SnsTemplate snsTemplate;

    // Topic ARN is read from application.properties
    // aws.sns.order-topic-arn=arn:aws:sns:ap-southeast-2:984454382434:order-events
    @Value("${aws.sns.order-topic-arn}")
    private String topicArn;

    /**
     * Publishes ORDER_CREATED event to SNS.
     *
     * message attribute: eventType = ORDER_CREATED
     *   → notification-queue subscription filter matches → queue receives this message
     *   → analytics-queue has no filter → also receives this message
     *
     * Both queues receive it SIMULTANEOUSLY — that is the fan-out.
     */
    public void publishOrderCreated(String orderId, String customerId) {
        snsTemplate.send(
                topicArn,
                MessageBuilder
                        .withPayload(new OrderEvent(orderId, customerId, "ORDER_CREATED"))
                        // message attributes — SNS filter policies check these
                        .setHeader("eventType", "ORDER_CREATED")
                        .build()
        );

        log.info("Published ORDER_CREATED event — orderId={} customerId={}", orderId, customerId);
    }

    /**
     * Publishes ORDER_DELIVERED event to SNS.
     *
     * message attribute: eventType = ORDER_DELIVERED
     *   → notification-queue subscription filter: eventType=ORDER_CREATED only
     *     → this message does NOT match → notification-queue does NOT receive it
     *   → analytics-queue has no filter → receives it
     *
     * This shows how filter policies route different event types to different consumers.
     */
    public void publishOrderDelivered(String orderId, String customerId) {
        snsTemplate.send(
                topicArn,
                MessageBuilder
                        .withPayload(new OrderEvent(orderId, customerId, "ORDER_DELIVERED"))
                        .setHeader("eventType", "ORDER_DELIVERED")
                        .build()
        );

        log.info("Published ORDER_DELIVERED event — orderId={} customerId={}", orderId, customerId);
    }

    // Event payload — serialized to JSON by SnsTemplate automatically
    record OrderEvent(String orderId, String customerId, String status) {}
}
