package com.example.eip.channels;

import java.util.concurrent.atomic.AtomicLong;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.pubsub.PubSubCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RedisDemoDataGenerator extends RouteBuilder {

    @Inject
    RedisDataSource redis;

    @ConfigProperty(name = "eip.redis.demo.data.generator.enabled", defaultValue = "true")
    boolean enabled;

    private final AtomicLong counter = new AtomicLong();

    @Override
    public void configure() {
        from("timer:redis-publisher?period=8000&delay=5000")
            .routeId("redis-pubsub-publisher")
            .autoStartup(enabled)
            .process(exchange -> {
                long id = counter.incrementAndGet();
                String json = """
                {"order_id": %d, "customer_id": "CUST-%03d", "event": "status_update", "status": "SHIPPED"}
                """.formatted(id, id % 100).strip();

                PubSubCommands<String> pubsub = redis.pubsub(String.class);
                pubsub.publish("eip.orders.notifications", json);
                exchange.getIn().setBody(json);
            })
            .log("Redis Pub/Sub → published notification: ${body}");
    }
}
