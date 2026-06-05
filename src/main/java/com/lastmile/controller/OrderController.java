package com.lastmile.controller;

import com.lastmile.dynamodb.DeliveryRecord;
import com.lastmile.dynamodb.DeliveryRepository;
import com.lastmile.sns.OrderSnsPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST API for Orders — ties together DynamoDB (2.5-2.7) and SNS (2.11-2.12)
 *
 * Flow for POST /orders:
 *   1. Save order to DynamoDB (pk=ORDER#<id>, sk=METADATA)
 *   2. Publish ORDER_CREATED to SNS topic
 *   3. SNS fans out to notification-queue and analytics-queue simultaneously
 */
@Tag(name = "Orders", description = "Order CRUD + SNS event publishing")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final DeliveryRepository repository;
    private final OrderSnsPublisher snsPublisher;

    @Operation(summary = "Create order — saves to DynamoDB + publishes ORDER_CREATED to SNS")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryRecord createOrder(@RequestBody CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();

        DeliveryRecord record = new DeliveryRecord();
        record.setPk("ORDER#" + orderId);
        record.setSk("METADATA");
        record.setCustomerId(request.customerId());
        record.setStatus("PENDING");
        // TTL: auto-expire after 7 days
        record.setTtl(Instant.now().plusSeconds(7 * 24 * 60 * 60).getEpochSecond());

        repository.save(record);

        // Publish to SNS → fans out to notification-queue + analytics-queue
        snsPublisher.publishOrderCreated(orderId, request.customerId());

        return record;
    }

    @Operation(summary = "Get order by ID")
    @GetMapping("/{orderId}")
    public DeliveryRecord getOrder(@PathVariable String orderId) {
        return repository.findByKey("ORDER#" + orderId, "METADATA");
    }

    @Operation(summary = "Get all orders for a customer (GSI query)")
    @GetMapping("/customer/{customerId}")
    public List<DeliveryRecord> getByCustomer(@PathVariable String customerId) {
        return repository.findByCustomerId(customerId);
    }

    @Operation(summary = "Mark order as delivered — publishes ORDER_DELIVERED to SNS")
    @PutMapping("/{orderId}/deliver")
    public DeliveryRecord deliverOrder(@PathVariable String orderId) {
        DeliveryRecord record = repository.findByKey("ORDER#" + orderId, "METADATA");
        record.setStatus("DELIVERED");
        repository.update(record);

        // ORDER_DELIVERED only goes to analytics-queue (filter policy blocks notification-queue)
        snsPublisher.publishOrderDelivered(orderId, record.getCustomerId());

        return record;
    }

    @Operation(summary = "Delete order")
    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@PathVariable String orderId) {
        repository.delete("ORDER#" + orderId, "METADATA");
    }

    record CreateOrderRequest(String customerId) {}
}
