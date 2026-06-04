package com.lastmile.dynamodb;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * =============================================================================
 * 2.7 — DynamoDB Optimistic Locking, TTL, PITR
 * =============================================================================
 *
 * ── OPTIMISTIC LOCKING ────────────────────────────────────────────────────────
 * Problem: Two services read the same item at the same time and both try to update it.
 * Without locking → last write wins → one update is silently lost.
 *
 * Optimistic locking solution:
 *  1. Item has a version number (starts at 1, increments on every write)
 *  2. When you update, DynamoDB checks: "is current version still N?"
 *  3. If YES  → update succeeds, version becomes N+1
 *  4. If NO   → someone else updated it first → ConditionalCheckFailedException
 *  5. You catch the exception and retry by reading the fresh item first
 *
 * In code: just add @DynamoDbVersionAttribute to a field — Enhanced Client does the rest.
 *
 * ── TTL (Time To Live) ────────────────────────────────────────────────────────
 * Problem: Old/expired items accumulate and you pay for storing them.
 *
 * TTL solution:
 *  1. Add a 'ttl' attribute to items (epoch seconds)
 *  2. Enable TTL on the table pointing to that attribute
 *  3. DynamoDB checks periodically and auto-deletes expired items for FREE
 *
 * Important notes:
 *  - TTL deletion is NOT instant — can take minutes to 48 hours
 *  - Deleted items still appear in Streams (marked as REMOVE with userIdentity=TimeToLiveDeletetion)
 *  - Use case: draft orders (expire after 24h), sessions, temp data
 *
 * ── PITR (Point-In-Time Recovery) ────────────────────────────────────────────
 * Problem: Someone accidentally deletes or corrupts data.
 *
 * PITR solution:
 *  - DynamoDB continuously backs up your table (every second for last 35 days)
 *  - You can restore to ANY second in that window
 *  - Restore creates a NEW table — original stays intact
 *  - Small additional cost but worth it for production data
 *  - Enable via CloudFormation or SDK (see enablePitr below)
 * =============================================================================
 */
@Service
public class DynamoDbFeaturesService {

    private final DynamoDbTable<DeliveryRecord> table;
    private final DynamoDbClient dynamoDbClient;

    public DynamoDbFeaturesService(DynamoDbEnhancedClient enhancedClient, DynamoDbClient dynamoDbClient) {
        this.table = enhancedClient.table("delivery-platform", TableSchema.fromBean(DeliveryRecord.class));
        this.dynamoDbClient = dynamoDbClient;
    }

    // ── Optimistic Locking: Update with version check ─────────────────────────
    // @DynamoDbVersionAttribute on DeliveryRecord.version handles everything.
    // updateItem automatically adds: condition "version = currentVersion"
    // If another process changed the item → version mismatch → exception → retry
    public void updateWithOptimisticLock(DeliveryRecord record) {
        try {
            table.updateItem(r -> r.item(record));
        } catch (ConditionalCheckFailedException e) {
            // This means another process updated the same item between our read and write.
            // Solution: read the item again to get the latest version, then retry the update.
            throw new RuntimeException(
                "Concurrent update conflict on pk=" + record.getPk() +
                ". Read the latest version and retry.", e);
        }
    }

    // ── TTL: Set expiry on an item ────────────────────────────────────────────
    // Sets the ttl field to N hours from now as epoch seconds.
    // After saving this record, DynamoDB will auto-delete it when ttl expires.
    public DeliveryRecord setExpiry(DeliveryRecord record, int hoursFromNow) {
        // Instant.now() + hoursFromNow converted to epoch seconds
        long expiryEpochSeconds = Instant.now()
                .plus(hoursFromNow, ChronoUnit.HOURS)
                .getEpochSecond();

        record.setTtl(expiryEpochSeconds);
        return record;
    }

    // ── TTL: Enable on the table ─────────────────────────────────────────────
    // Run once per table. Points DynamoDB to the attribute that holds the TTL value.
    // Attribute must contain epoch seconds (not milliseconds).
    public void enableTtl(String tableName) {
        dynamoDbClient.updateTimeToLive(r -> r
                .tableName(tableName)
                .timeToLiveSpecification(spec -> spec
                        .attributeName("ttl")  // must match the attribute name in DeliveryRecord
                        .enabled(true)));
    }

    // ── PITR: Enable Point-In-Time Recovery ──────────────────────────────────
    // Run once per table. After this, DynamoDB continuously backs up the table.
    // To restore: go to AWS Console → DynamoDB → Tables → Backups → Restore to point in time
    public void enablePitr(String tableName) {
        dynamoDbClient.updateContinuousBackups(r -> r
                .tableName(tableName)
                .pointInTimeRecoverySpecification(spec -> spec
                        .pointInTimeRecoveryEnabled(true)));
    }
}
