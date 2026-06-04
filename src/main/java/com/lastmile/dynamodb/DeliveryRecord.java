package com.lastmile.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

/**
 * =============================================================================
 * 2.5 — DynamoDB Core Concepts, Data Modeling, Single-Table Design
 * =============================================================================
 *
 * WHAT IS SINGLE-TABLE DESIGN?
 * In relational DBs you have one table per entity (orders, items, deliveries).
 * In DynamoDB the best practice is ONE table for ALL entities.
 *
 * WHY?
 * DynamoDB charges per read/write operation.
 * If you need order + its items → 2 separate tables = 2 round trips = 2x cost.
 * Single table = get everything in 1 query.
 *
 * HOW?
 * Use generic PK/SK names like pk and sk.
 * Put different entity types in the same table using key prefixes.
 *
 * KEY DESIGN for this delivery platform:
 * ┌─────────────────────┬──────────────────────┬─────────────────────────┐
 * │ PK                  │ SK                   │ Entity                  │
 * ├─────────────────────┼──────────────────────┼─────────────────────────┤
 * │ ORDER#ord-001       │ METADATA             │ Order header            │
 * │ ORDER#ord-001       │ ITEM#item-001         │ Order line item 1       │
 * │ ORDER#ord-001       │ ITEM#item-002         │ Order line item 2       │
 * │ DELIVERY#ord-001    │ STATUS#2024-01-01T10  │ Delivery status update  │
 * └─────────────────────┴──────────────────────┴─────────────────────────┘
 *
 * ACCESS PATTERNS (design your keys around these):
 *  1. Get order header           → PK=ORDER#<id>     SK=METADATA
 *  2. Get all items for order    → PK=ORDER#<id>     SK begins_with ITEM#
 *  3. Get all orders by customer → GSI customerId-index, GSI PK = customerId
 *  4. Get delivery history       → PK=DELIVERY#<id>  SK begins_with STATUS#
 * =============================================================================
 */
@DynamoDbBean
public class DeliveryRecord {

    // ── Primary Key ───────────────────────────────────────────────────────────
    // PK uses entity type prefix so we can store multiple entity types in one table
    // Examples: ORDER#ord-001, DELIVERY#ord-001
    private String pk;

    // SK narrows down within a PK partition
    // Examples: METADATA, ITEM#item-001, STATUS#2024-01-01T10:00:00
    private String sk;

    // ── GSI attribute ─────────────────────────────────────────────────────────
    // Global Secondary Index allows querying by customerId
    // Without GSI: you'd have to scan the whole table to find a customer's orders
    // With GSI: direct query by customerId → fast and cheap
    private String customerId;

    // ── Common attributes shared across entity types ──────────────────────────
    private String status;     // ORDER: PENDING/CONFIRMED | DELIVERY: IN_TRANSIT/DELIVERED
    private String itemName;   // used when sk starts with ITEM#
    private Integer quantity;  // used when sk starts with ITEM#

    // ── TTL (Time To Live) ────────────────────────────────────────────────────
    // DynamoDB auto-deletes items when current time > ttl value
    // ttl must be stored as epoch seconds (Unix timestamp)
    // Use case: expire draft orders after 24h, clean up old status records
    // DynamoDB does NOT delete instantly — usually within minutes but can take up to 48h
    private Long ttl;

    // ── Version (Optimistic Locking) ─────────────────────────────────────────
    // DynamoDB auto-increments version on every write
    // On update: adds condition "only write if version = N"
    // If two processes update at the same time → one fails → retry with fresh data
    // This prevents lost updates without using transactions
    private Integer version;

    // ── Getters and Setters with DynamoDB annotations ─────────────────────────
    // DynamoDB Enhanced Client reads annotations on GETTERS not fields

    @DynamoDbPartitionKey
    public String getPk()               { return pk; }
    public void setPk(String v)         { this.pk = v; }

    @DynamoDbSortKey
    public String getSk()               { return sk; }
    public void setSk(String v)         { this.sk = v; }

    // marks this field as the GSI partition key for "customerId-index"
    @DynamoDbSecondaryPartitionKey(indexNames = "customerId-index")
    public String getCustomerId()       { return customerId; }
    public void setCustomerId(String v) { this.customerId = v; }

    public String getStatus()           { return status; }
    public void setStatus(String v)     { this.status = v; }

    public String getItemName()         { return itemName; }
    public void setItemName(String v)   { this.itemName = v; }

    public Integer getQuantity()        { return quantity; }
    public void setQuantity(Integer v)  { this.quantity = v; }

    // DynamoDB uses the attribute name "ttl" to identify the TTL field
    @DynamoDbAttribute("ttl")
    public Long getTtl()                { return ttl; }
    public void setTtl(Long v)          { this.ttl = v; }

    // tells Enhanced Client to auto-manage version for optimistic locking
    @DynamoDbVersionAttribute
    public Integer getVersion()         { return version; }
    public void setVersion(Integer v)   { this.version = v; }
}
