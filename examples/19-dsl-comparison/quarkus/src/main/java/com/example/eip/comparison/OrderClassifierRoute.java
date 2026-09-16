package com.example.eip.comparison;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class OrderClassifierRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:eip.orders.placed?brokers={{kafka.brokers}}"
                + "&groupId=dsl-comparison&autoOffsetReset=earliest")
            .routeId("order-classifier")
            .unmarshal().json(java.util.Map.class)
            .choice()
                .when(simple("${body[amount]} > 500"))
                    .setHeader("tier", constant("HIGH_VALUE"))
                .otherwise()
                    .setHeader("tier", constant("STANDARD"))
            .end()
            .log("Order ${body[order_id]} amount=${body[amount]} tier=${header.tier}")
            .marshal().json()
            .to("kafka:eip.orders.classified?brokers={{kafka.brokers}}");
    }
}

