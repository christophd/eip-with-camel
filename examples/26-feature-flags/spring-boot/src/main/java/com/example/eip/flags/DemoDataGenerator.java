package com.example.eip.flags;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits one order every five seconds, cycling customer tiers so the targeted
 * rollout flag has something to discriminate on.
 */
@Component
public class DemoDataGenerator extends RouteBuilder {

    private static final String[] TIERS = {"STANDARD", "ENTERPRISE", "STANDARD", "VIP"};

    private final AtomicLong counter = new AtomicLong();

    @Override
    public void configure() {
        from("timer:flag-demo?period=5000")
            .routeId("demo-data-generator")
            .autoStartup("{{eip.demo.generator.enabled:true}}")
            .process(exchange -> {
                long n = counter.getAndIncrement();
                String tier = TIERS[(int) (n % TIERS.length)];
                exchange.getIn().setBody(
                    "{\"order_id\":\"ORD-" + (5000 + n) + "\","
                        + "\"customer_tier\":\"" + tier + "\","
                        + "\"item_sku\":\"WIDGET-1\",\"quantity\":2,\"amount\":149.99}");
            })
            .to("kafka:eip.orders.placed?brokers={{kafka.brokers}}");
    }
}
