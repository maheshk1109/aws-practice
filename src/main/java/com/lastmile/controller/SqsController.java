package com.lastmile.controller;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for testing SQS directly — topics 2.8, 2.9, 2.10
 *
 * Use this to:
 *  - Send a test message to order-queue
 *  - Verify DLQ behaviour by sending a message with body "FAIL"
 *    (BatchFailureHandler.java returns it as a batch failure → goes to DLQ after maxReceiveCount)
 */
@Tag(name = "SQS", description = "Send messages to SQS queues for testing")
@RestController
@RequestMapping("/sqs")
@RequiredArgsConstructor
public class SqsController {

    private final SqsTemplate sqsTemplate;

    @Value("${aws.sqs.order-queue}")
    private String orderQueue;

    @Operation(summary = "Send a message to order-queue. Send body=FAIL to test DLQ routing.")
    @PostMapping("/send")
    public String sendMessage(@RequestBody String message) {
        sqsTemplate.send(orderQueue, message);
        return "Sent to " + orderQueue + ": " + message;
    }
}
