package com.example.eip.routing;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class SplitterRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:eip.orders.batch?brokers={{kafka.brokers}}&groupId=splitter-demo")
            .routeId("order-splitter")
            .unmarshal().json()
            .log("Batch received with ${body[items].size()} items")
            .split(jsonpath("$.items[*]"))
                .log("Processing item: ${body[item_sku]} qty=${body[quantity]}")
                .marshal().json()
                .to("kafka:eip.orders.individual?brokers={{kafka.brokers}}")
            .end();

        // Partial failure. A bad line item should not abandon the rest of the
        // order, but a batch that is mostly bad means the upstream is broken
        // and there is no point fulfilling half of it. maxFailedRecords draws
        // that line: with maxFailedRecords(5) the split aborts on the fifth
        // failing item, throwing CamelExchangeException. Verified 2026-09-15 --
        // 3 bad of 10 completes, 8 bad of 10 aborts at the fifth.
        //
        // An absolute count rather than errorThreshold(0.1) on purpose: with
        // parallelProcessing, split items finish in non-deterministic order, so
        // the running failure *ratio* varies between runs on identical input.
        //
        // Note there is deliberately no doTry/doCatch around the failing step.
        // Catching the exception makes the sub-exchange *succeed*, so the
        // splitter counts nothing and maxFailedRecords never fires. If you want
        // both per-item recovery and a batch-level abort, the recovery has to
        // rethrow.
        from("kafka:eip.orders.batch.tolerant?brokers={{kafka.brokers}}&groupId=splitter-tolerant")
            .routeId("order-splitter-tolerant")
            .unmarshal().json()
            .log("Tolerant batch received with ${body[items].size()} items")
            .split(jsonpath("$.items[*]"))
                .maxFailedRecords(5)
                .process(exchange -> {
                    @SuppressWarnings("unchecked")
                    var item = exchange.getIn().getBody(java.util.Map.class);
                    Object qty = item.get("quantity");
                    if (qty == null || Integer.parseInt(qty.toString()) <= 0) {
                        throw new IllegalArgumentException(
                            "Invalid quantity for SKU " + item.get("item_sku"));
                    }
                })
                .marshal().json()
                .to("kafka:eip.orders.individual?brokers={{kafka.brokers}}")
            .end()
            .log("Tolerant batch dispatched");

        // Chunking. group(n) hands the route a List body of n items instead of
        // one item at a time, which is what you want when the per-item cost is
        // a round trip -- 500 inserts versus 20 batches of 25.
        from("kafka:eip.orders.batch.chunked?brokers={{kafka.brokers}}&groupId=splitter-chunked")
            .routeId("order-splitter-chunked")
            .unmarshal().json()
            .log("Chunked batch received with ${body[items].size()} items")
            .split(jsonpath("$.items[*]")).group(25)
                .log("Processing a chunk of ${body.size()} line items")
                .marshal().json()
                .to("kafka:eip.orders.chunks?brokers={{kafka.brokers}}")
            .end();
    }
}
