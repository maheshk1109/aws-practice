package com.lastmile.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

/**
 * =============================================================================
 * 2.16 — Lambda: Basics, Handler Pattern, Event Sources
 * =============================================================================
 *
 * WHAT IS LAMBDA?
 * Lambda lets you run code without managing servers.
 * You upload a JAR, configure the handler method, and AWS runs it when triggered.
 * AWS handles: server provisioning, scaling, OS patching, capacity planning.
 * You pay only when your code runs — no charge when idle.
 *
 * HANDLER PATTERN:
 * Every Lambda handler implements RequestHandler<INPUT, OUTPUT>
 *   INPUT  = the event type that triggers this Lambda
 *   OUTPUT = what the Lambda returns to the caller
 *
 * For SQS trigger:  RequestHandler<SQSEvent, Void>      (no return needed)
 * For API Gateway:  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent>
 * For DynamoDB:     RequestHandler<DynamodbEvent, Void>  (already in stream-handler)
 * For S3:           RequestHandler<S3Event, Void>
 *
 * EVENT SOURCES (what can trigger a Lambda):
 *   SQS           → Lambda polls the queue, invokes with batch of messages
 *   SNS           → SNS pushes message to Lambda directly
 *   DynamoDB Streams → Lambda polls the stream
 *   API Gateway   → HTTP request triggers Lambda
 *   EventBridge   → scheduled or event-based trigger
 *   S3            → file upload/delete triggers Lambda
 *
 * COLD START vs WARM START:
 *   Cold start = first invocation after Lambda is idle
 *     → JVM boots, classes load, Spring context initializes (~2-5 seconds for Java)
 *   Warm start = subsequent invocations while instance is still alive
 *     → reuses existing JVM instance (~milliseconds)
 *   SnapStart (2.17) solves the cold start problem for Java.
 *
 * CONTEXT OBJECT:
 *   Provides: function name, remaining time, memory limit, request ID, logger.
 *   context.getLogger().log() writes to CloudWatch Logs automatically.
 * =============================================================================
 */
public class OrderEventHandler implements RequestHandler<SQSEvent, Void> {

    /**
     * Lambda runtime calls this method when SQS delivers messages.
     * event.getRecords() contains the batch of SQS messages (up to BatchSize).
     * Each message is processed independently inside the loop.
     *
     * If this method throws an exception → entire batch retries (bad for duplicates).
     * Use ReportBatchItemFailures (2.18) to retry only failed messages.
     */
    @Override
    public Void handleRequest(SQSEvent event, Context context) {

        // context.getLogger() writes directly to CloudWatch Logs
        // No logging framework needed — Lambda handles it
        context.getLogger().log("Received batch of " + event.getRecords().size() + " messages");

        for (SQSEvent.SQSMessage message : event.getRecords()) {

            // messageId is unique per message — useful for idempotency checks
            context.getLogger().log("MessageId  : " + message.getMessageId());

            // body contains your actual payload (JSON string)
            context.getLogger().log("Body       : " + message.getBody());

            // eventSourceArn tells you which queue sent this message
            context.getLogger().log("Source ARN : " + message.getEventSourceArn());

            processMessage(message, context);
        }

        // return null for Void — SQS trigger does not use the return value
        return null;
    }

    private void processMessage(SQSEvent.SQSMessage message, Context context) {
        // parse message.getBody() as JSON and process
        // context.getRemainingTimeInMillis() tells you how much time is left before timeout
        context.getLogger().log("Processing: " + message.getBody()
                + " | Time remaining: " + context.getRemainingTimeInMillis() + "ms");
    }
}
