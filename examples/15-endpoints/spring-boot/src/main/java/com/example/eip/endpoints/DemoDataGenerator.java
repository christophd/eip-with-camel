package com.example.eip.endpoints;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DemoDataGenerator extends RouteBuilder {

    @Value("${eip.demo.data.generator.enabled:true}")
    boolean enabled;

    private final AtomicLong counter = new AtomicLong();

    @Override
    public void configure() {
        from("timer:demo-orders?period=5000&delay=3000")
            .routeId("demo-data-generator")
            .autoStartup(enabled)
            .process(exchange -> {
                long id = counter.incrementAndGet();
                double amount = 50 + (id * 37 % 500);

                String json = """
                    {
                        "order_id": %d,
                        "customer_id": "CUST-%03d",
                        "item_sku": "SKU-%04d",
                        "quantity": %d,
                        "amount": %.2f
                    }
                    """.formatted(
                        id,
                        id % 100,
                        id % 9999,
                        (int) (1 + id % 5),
                        amount
                    );
                exchange.getIn().setBody(json);
                exchange.getIn().setHeader("kafka.KEY", String.valueOf(id));
            })
            .multicast()
                .to("kafka:eip.orders.placed?brokers={{kafka.brokers}}")
                .to("kafka:eip.payments.required?brokers={{kafka.brokers}}")
            .end()
            .log("Generated order ${header.kafka.KEY} → eip.orders.placed + eip.payments.required");
    }
}
