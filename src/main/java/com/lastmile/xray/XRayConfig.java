package com.lastmile.xray;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.plugins.ECSPlugin;
import com.amazonaws.xray.strategy.sampling.CentralizedSamplingStrategy;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 2.20 — AWS X-Ray Tracing Setup
 *
 * WHAT IS X-RAY?
 *   AWS X-Ray traces requests as they flow through your application and AWS services.
 *   It shows you a service map, latency breakdown, and where errors occur.
 *
 * HOW IT WORKS:
 *   1. X-Ray daemon runs as a sidecar (ECS) or is built into Lambda/Elastic Beanstalk
 *   2. Your app sends trace segments to the daemon via UDP on port 2000
 *   3. Daemon forwards them to X-Ray service
 *   4. You see traces in AWS Console → X-Ray → Traces
 *
 * KEY CONCEPTS:
 *   - Trace      : one full request end-to-end (has a unique trace ID)
 *   - Segment    : work done by one service (e.g. your Spring Boot app)
 *   - Subsegment : finer breakdown within a segment (DB call, HTTP call, etc.)
 *   - Annotation : indexed key-value — used to FILTER traces in the console
 *   - Metadata   : non-indexed key-value — useful for debugging, not searchable
 *
 * SAMPLING:
 *   X-Ray does NOT record every request (too expensive).
 *   CentralizedSamplingStrategy reads sampling rules from X-Ray service —
 *   you can change rules in AWS Console without redeploying.
 *
 * ECSPlugin:
 *   Automatically adds ECS task ARN and cluster name to every segment.
 *   Use EC2Plugin for EC2, ElasticBeanstalkPlugin for EB.
 */
@Configuration
public class XRayConfig {

    @PostConstruct
    public void initXRay() {
        // Build the global recorder with:
        //  - ECSPlugin: auto-adds ECS task/cluster info to each segment
        //  - CentralizedSamplingStrategy: reads sampling rules from X-Ray service
        //    (fallback: samples 5% if X-Ray service is unreachable)
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard()
                .withPlugin(new ECSPlugin())
                .withSamplingStrategy(new CentralizedSamplingStrategy());

        // Set as the global recorder — use AWSXRay.getGlobalRecorder() anywhere
        AWSXRay.setGlobalRecorder(builder.build());
    }
}
