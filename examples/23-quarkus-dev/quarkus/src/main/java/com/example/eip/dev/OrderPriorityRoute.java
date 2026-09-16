package com.example.eip.dev;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

import java.util.Map;

/**
 * The route to edit while dev mode is running.
 *
 * <p>Change the threshold below, save, and send another order. Quarkus reloads
 * the route on the next request -- no restart, no rebuild. The continuous test
 * in {@code OrderPriorityRouteTest} re-runs at the same moment and will go red
 * if you move the threshold without updating it, which is the point: dev mode
 * tells you that you broke something before you have finished reading the log.
 */
@ApplicationScoped
public class OrderPriorityRoute extends RouteBuilder {

    /** Edit me while dev mode is running. */
    static final double PRIORITY_THRESHOLD = 500.00;

    @Override
    public void configure() {
        from("kafka:eip.dev.orders?brokers={{kafka.bootstrap.servers}}"
                + "&groupId=dev-priority&autoOffsetReset=earliest")
            .routeId("order-priority")
            .unmarshal().json(Map.class)
            .to("direct:classify-priority");

        from("direct:classify-priority")
            .routeId("classify-priority")
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> order = exchange.getIn().getBody(Map.class);
                double amount = ((Number) order.getOrDefault("amount", 0)).doubleValue();
                order.put("priority", amount >= PRIORITY_THRESHOLD ? "EXPEDITED" : "STANDARD");
            })
            .log("Order ${body[order_id]} amount=${body[amount]} -> ${body[priority]}")
            .to("mock:classified");
    }
}
