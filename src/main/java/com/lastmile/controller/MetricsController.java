package com.lastmile.controller;

import com.lastmile.cloudwatch.CloudWatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for testing CloudWatch custom metrics — topics 2.18, 2.19
 *
 * After calling POST /metrics/publish, go to:
 *   AWS Console → CloudWatch → Metrics → Custom Namespaces → LastMile/Orders
 * You should see the metric appear within 1-2 minutes.
 */
@Tag(name = "Metrics", description = "Publish custom CloudWatch metrics")
@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final CloudWatchService cloudWatchService;

    @Operation(summary = "Publish a custom metric to CloudWatch (LastMile/Orders namespace)")
    @PostMapping("/publish")
    public String publishMetric(@RequestParam String queueName, @RequestParam double count) {
        cloudWatchService.publishOrdersProcessedMetric(queueName, count);
        return "Published metric: OrdersProcessed = " + count + " for queue=" + queueName + " to LastMile/Orders namespace";
    }
}
