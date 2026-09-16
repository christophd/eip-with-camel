package com.example.eip.dev;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

import java.util.concurrent.atomic.AtomicLong;

/** Emits an order every five seconds so there is always something to reload against. */
@ApplicationScoped
public class DemoDataGenerator extends RouteBuilder {

    private static final double[] AMOUNTS = {129.99, 640.00, 75.50, 1299.00};

    private final AtomicLong counter = new AtomicLong();

    @Override
    public void configure() {
        from("timer:dev-demo?period=5000")
            .routeId("demo-data-generator")
            .autoStartup("{{eip.demo.generator.enabled:true}}")
            .process(exchange -> {
                long n = counter.getAndIncrement();
                exchange.getIn().setBody(
                    "{\"order_id\":\"ORD-" + (7000 + n) + "\","
                        + "\"item_sku\":\"WIDGET-1\",\"quantity\":1,"
                        + "\"amount\":" + AMOUNTS[(int) (n % AMOUNTS.length)] + "}");
            })
            .to("kafka:eip.dev.orders?brokers={{kafka.bootstrap.servers}}");
    }
}
