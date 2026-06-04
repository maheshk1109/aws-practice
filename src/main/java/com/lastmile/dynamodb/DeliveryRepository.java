package com.lastmile.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * =============================================================================
 * 2.6 — DynamoDB Enhanced Client: CRUD, GSI, Streams
 * =============================================================================
 *
 * WHAT IS THE ENHANCED CLIENT?
 * AWS SDK v2 has two DynamoDB clients:
 *
 *  DynamoDbClient (low-level):
 *    → You write raw attribute maps: Map.of("pk", AttributeValue.fromS("ORDER#1"))
 *    → Verbose, error-prone, no type safety
 *
 *  DynamoDbEnhancedClient (high-level):
 *    → Maps Java objects to DynamoDB items automatically using @DynamoDbBean annotations
 *    → You work with DeliveryRecord objects directly — no raw attribute maps
 *    → Type safe, much cleaner code
 *
 * HOW IT WORKS:
 *  1. Annotate your class with @DynamoDbBean (see DeliveryRecord.java)
 *  2. Create a DynamoDbTable<DeliveryRecord> pointing to your table
 *  3. Call putItem / getItem / updateItem / deleteItem / query
 * =============================================================================
 */
@Repository
public class DeliveryRepository {

    private final DynamoDbTable<DeliveryRecord> table;
    private final DynamoDbIndex<DeliveryRecord> customerIndex;

    /**
     * DynamoDbEnhancedClient is auto-created by Spring Cloud AWS if
     * dynamodb-enhanced is on the classpath and region is configured.
     * TableSchema.fromBean() reads the @DynamoDbBean annotations on DeliveryRecord
     * and builds the mapping between Java fields and DynamoDB attributes.
     */
    public DeliveryRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("delivery-platform", TableSchema.fromBean(DeliveryRecord.class));

        // GSI reference — used for querying by customerId
        // Index name must match what is configured on the DynamoDB table
        this.customerIndex = table.index("customerId-index");
    }

    // ── CREATE ────────────────────────────────────────────────────────────────
    // putItem = insert or replace entirely (no partial update)
    // If item with same PK+SK already exists it gets overwritten
    public void save(DeliveryRecord record) {
        table.putItem(record);
    }

    // ── READ by exact PK + SK ─────────────────────────────────────────────────
    // getItem requires both PK and SK — returns null if not found
    // Example: get order header → pk="ORDER#ord-001", sk="METADATA"
    public DeliveryRecord findByKey(String pk, String sk) {
        return table.getItem(Key.builder()
                .partitionValue(pk)   // PK value
                .sortValue(sk)        // SK value
                .build());
    }

    // ── READ all items under one PK ───────────────────────────────────────────
    // query returns ALL items where PK matches — multiple SK values
    // Example: all line items for an order → PK="ORDER#ord-001", SK starts with "ITEM#"
    public List<DeliveryRecord> findAllByPk(String pk) {
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue(pk)
                        .build());

        // query() returns pages — flatMap collects all items across pages
        return table.query(condition)
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    // updateItem does a partial update — only sets attributes present in the record
    // Also handles optimistic locking automatically via @DynamoDbVersionAttribute
    // If version mismatch → throws ConditionalCheckFailedException → caller should retry
    public void update(DeliveryRecord record) {
        table.updateItem(r -> r.item(record));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    // deleteItem removes the item permanently
    // No soft delete in DynamoDB — use status="DELETED" if you need audit trail
    public void delete(String pk, String sk) {
        table.deleteItem(Key.builder()
                .partitionValue(pk)
                .sortValue(sk)
                .build());
    }

    // ── GSI QUERY — find all orders for a customer ────────────────────────────
    // Without GSI: you would scan the ENTIRE table and filter → expensive
    // With GSI:    DynamoDB maintains a separate index sorted by customerId → fast
    // GSI query syntax is identical to table query — just call it on the index reference
    public List<DeliveryRecord> findByCustomerId(String customerId) {
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue(customerId)  // GSI PK
                        .build());

        return customerIndex.query(condition)
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }
}
