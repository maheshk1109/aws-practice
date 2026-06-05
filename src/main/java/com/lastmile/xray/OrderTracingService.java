package com.lastmile.xray;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import org.springframework.stereotype.Service;

/**
 * 2.20 — X-Ray: Subsegments, Annotations, Metadata
 *
 * WHY SUBSEGMENTS?
 *   A segment covers your whole request. Subsegments let you drill into
 *   specific operations — "how long did the DynamoDB call take?" vs
 *   "how long did the entire /orders endpoint take?"
 *
 * ANNOTATIONS vs METADATA:
 *   - Annotations: putAnnotation("orderId", id)
 *       → Indexed. You can search/filter traces by these in X-Ray console.
 *       → Use for fields you'll query: orderId, customerId, status
 *       → Values must be String, Number, or Boolean
 *
 *   - Metadata: putMetadata("payload", obj)
 *       → NOT indexed. Stored in the trace but not searchable.
 *       → Use for large objects, debug payloads, stack traces
 *
 * TRACE HEADER PROPAGATION:
 *   X-Ray passes a trace header (X-Amzn-Trace-Id) between services.
 *   If you call another service via HTTP/SQS, include this header so
 *   X-Ray can stitch the traces together into one service map.
 *
 * IN PRODUCTION:
 *   AWS SDK v2 clients automatically create subsegments for DynamoDB,
 *   S3, SQS, SNS calls — you only need manual subsegments for your own logic.
 */
@Service
public class OrderTracingService {

    /**
     * Trace an order processing operation.
     *
     * The active segment is created automatically by the X-Ray servlet filter
     * (for HTTP requests) or by Lambda runtime (for Lambda functions).
     * You only need to add subsegments for inner operations.
     */
    public void processOrder(String orderId, String customerId) {
        // beginSubsegment creates a child of the current active segment
        Subsegment subsegment = AWSXRay.beginSubsegment("processOrder");
        try {
            // Annotations: indexed — searchable in X-Ray console
            // e.g. filter traces by annotation.orderId = "ORD-123"
            subsegment.putAnnotation("orderId", orderId);
            subsegment.putAnnotation("customerId", customerId);
            subsegment.putAnnotation("service", "OrderProcessor");

            // Metadata: not indexed — good for large debug payloads
            subsegment.putMetadata("input", "orderId=" + orderId + ", customerId=" + customerId);

            // Nested subsegment — shows DynamoDB lookup as its own block in trace
            traceDbLookup(orderId);

            subsegment.putAnnotation("status", "SUCCESS");

        } catch (Exception e) {
            // Mark subsegment as fault so X-Ray shows it in red
            subsegment.addException(e);
            subsegment.setError(true);
            throw e;
        } finally {
            // ALWAYS end the subsegment — memory leak if you forget
            AWSXRay.endSubsegment();
        }
    }

    private void traceDbLookup(String orderId) {
        Subsegment dbSeg = AWSXRay.beginSubsegment("DynamoDB::getOrder");
        try {
            dbSeg.putAnnotation("orderId", orderId);
            // Your actual DynamoDB call goes here
            // AWS SDK v2 would auto-instrument this if xray-recorder-aws-sdk is on classpath
        } finally {
            AWSXRay.endSubsegment();
        }
    }
}
