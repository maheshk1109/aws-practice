package com.lastmile.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.crac.Core;
import org.crac.Resource;

/**
 * =============================================================================
 * 2.17 — Lambda SnapStart, Cold Starts, Memory Tuning
 * =============================================================================
 *
 * THE COLD START PROBLEM (Java specific):
 * Java Lambda cold start is slow because:
 *   1. JVM must boot from scratch
 *   2. All classes must be loaded and JIT compiled
 *   3. If using Spring — entire application context must initialize
 * This can take 2-5 seconds, which is unacceptable for low-latency APIs.
 *
 * SNAPSTART SOLUTION:
 * SnapStart takes a snapshot of the Lambda execution environment AFTER
 * the init phase (after static blocks and constructors run).
 * On cold start: AWS restores from snapshot instead of booting from scratch.
 * Result: cold start drops from ~3000ms to ~200ms.
 *
 * HOW TO ENABLE:
 *   In CloudFormation: SnapStart: { ApplyOn: PublishedVersions }
 *   Only works on: java17, java21 runtimes
 *   Only works on: published versions — NOT $LATEST
 *   After deploy: must publish a new version for SnapStart to apply
 *
 * CRAC (Coordinated Restore at Checkpoint):
 * CRaC is the JVM mechanism that SnapStart uses internally.
 * It provides two lifecycle hooks you MUST implement if you have stateful resources:
 *
 *   beforeCheckpoint() — called just BEFORE the snapshot is taken
 *     → CLOSE: DB connections, HTTP clients, file handles, sockets
 *     → WHY: these are OS-level resources tied to the original machine
 *             they will be invalid/stale after restore on a different machine
 *
 *   afterRestore() — called just AFTER restore from snapshot
 *     → RE-OPEN: all connections you closed in beforeCheckpoint
 *     → WHY: you need fresh connections on the restored environment
 *
 * If you do NOT implement these hooks and have open connections:
 *   → Connections will be stale after restore
 *   → First request will fail with connection errors
 *
 * MEMORY TUNING:
 *   Lambda memory also controls CPU allocation — more memory = more CPU.
 *   128MB  → minimum, very slow for Java
 *   512MB  → good starting point for simple Java functions
 *   1024MB → recommended for Spring Boot Lambdas
 *   More memory = faster execution but higher cost per ms.
 *   Use AWS Lambda Power Tuning tool to find the optimal memory setting.
 * =============================================================================
 */
public class SnapStartHandler implements RequestHandler<SQSEvent, Void>, Resource {

    // Simulates a database connection that must be managed across checkpoint/restore
    private FakeDbConnection dbConnection;

    public SnapStartHandler() {
        // This constructor runs ONCE at snapshot time — not on every cold start
        // Heavy initialization (Spring context, DB connections) happens here
        this.dbConnection = new FakeDbConnection();
        this.dbConnection.connect();

        // Register with CRaC so beforeCheckpoint and afterRestore are called
        // If you skip this, CRaC won't manage your resources
        Core.getGlobalContext().register(this);
    }

    /**
     * Called BEFORE snapshot is taken.
     * Close everything that is OS-level or network-level.
     * After this runs, the JVM state is frozen into the snapshot.
     */
    @Override
    public void beforeCheckpoint(org.crac.Context<? extends Resource> context) throws Exception {
        // Close DB connection — it will be stale after restore on a different machine
        dbConnection.disconnect();
        System.out.println("beforeCheckpoint: DB connection closed");
    }

    /**
     * Called AFTER restore from snapshot.
     * Re-open everything you closed in beforeCheckpoint.
     * After this runs, handleRequest will be called with the actual event.
     */
    @Override
    public void afterRestore(org.crac.Context<? extends Resource> context) throws Exception {
        // Re-open DB connection on the restored execution environment
        dbConnection.connect();
        System.out.println("afterRestore: DB connection re-opened");
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("Handling " + event.getRecords().size() + " messages");

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            context.getLogger().log("Processing: " + message.getBody());
            // dbConnection is ready to use here — re-opened in afterRestore
        }

        return null;
    }
}

// ── Simulated DB connection to demonstrate beforeCheckpoint/afterRestore ─────
class FakeDbConnection {
    private boolean connected = false;

    void connect() {
        connected = true;
        System.out.println("FakeDbConnection: connected");
    }

    void disconnect() {
        connected = false;
        System.out.println("FakeDbConnection: disconnected");
    }

    boolean isConnected() {
        return connected;
    }
}
