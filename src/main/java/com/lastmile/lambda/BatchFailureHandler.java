package com.lastmile.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================================
 * 2.18 — Lambda Error Handling, Partial Batch Failure, DLQ
 * =============================================================================
 *
 * THE BATCH FAILURE PROBLEM:
 * Lambda processes SQS messages in batches (e.g. 10 messages at once).
 *
 * DEFAULT BEHAVIOUR (without ReportBatchItemFailures):
 *   Batch of 10 messages → message #5 fails → Lambda throws exception
 *   → SQS treats ALL 10 as failed → ALL 10 retry
 *   → Messages 1,2,3,4,6,7,8,9,10 get processed AGAIN → duplicates!
 *   → Message #5 also retries → eventually goes to DLQ
 *   → Result: 9 extra unnecessary retries causing duplicate processing
 *
 * WITH ReportBatchItemFailures:
 *   Batch of 10 messages → message #5 fails
 *   → Lambda returns: "only messageId of message #5 failed"
 *   → SQS deletes messages 1,2,3,4,6,7,8,9,10 (they succeeded)
 *   → SQS retries ONLY message #5
 *   → After maxReceiveCount retries → message #5 goes to DLQ
 *   → Result: no duplicates, only the actual bad message retries
 *
 * HOW TO ENABLE:
 *   In EventSourceMapping CloudFormation:
 *     FunctionResponseTypes: [ReportBatchItemFailures]
 *   Return SQSBatchResponse instead of Void
 *   Put failed messageIds in batchItemFailures list
 *
 * BisectBatchOnFunctionError:
 *   If Lambda THROWS (crashes entirely, not ReportBatchItemFailures),
 *   SQS splits the batch in half and retries each half separately.
 *   This helps isolate which specific message caused the crash.
 *
 * DLQ on Lambda:
 *   Lambda has its own DLQ setting (separate from SQS queue's DLQ).
 *   Lambda DLQ receives events when Lambda itself fails (async invocations).
 *   For SQS-triggered Lambda: use the SQS queue's DLQ — not Lambda's DLQ.
 * =============================================================================
 */
public class BatchFailureHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    /**
     * Returns SQSBatchResponse instead of Void.
     * SQSBatchResponse.batchItemFailures contains messageIds that failed.
     * SQS will retry only those messages — all others are deleted from queue.
     */
    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {

        // collect failed messageIds here — starts empty (assume all succeed)
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processMessage(message, context);
                // if no exception → message succeeded → NOT added to failures list
                // SQS will delete this message from the queue
                context.getLogger().log("SUCCESS: " + message.getMessageId());

            } catch (Exception e) {
                context.getLogger().log("FAILED:  " + message.getMessageId() + " — " + e.getMessage());

                // add this messageId to failures
                // SQS will NOT delete this message → it will retry after VisibilityTimeout
                // after maxReceiveCount retries → it goes to DLQ
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        context.getLogger().log("Batch complete: "
                + (event.getRecords().size() - failures.size()) + " succeeded, "
                + failures.size() + " failed");

        // return the failures list — if empty, SQS deletes all messages in the batch
        return SQSBatchResponse.builder()
                .withBatchItemFailures(failures)
                .build();
    }

    private void processMessage(SQSEvent.SQSMessage message, Context context) {
        String body = message.getBody();
        context.getLogger().log("Processing body: " + body);

        // ── HOW TO TEST THE FAILURE PATH ─────────────────────────────────────
        // Send a message with body containing "FAIL" to trigger this exception.
        // Expected behaviour:
        //   → only that message retries
        //   → all other messages in the batch succeed and are deleted from SQS
        //   → after 3 retries (maxReceiveCount) → message goes to DLQ
        if (body.contains("FAIL")) {
            throw new RuntimeException("Simulated failure — body contained FAIL keyword");
        }

        // real processing logic goes here (parse JSON, call DB, etc.)
    }
}
