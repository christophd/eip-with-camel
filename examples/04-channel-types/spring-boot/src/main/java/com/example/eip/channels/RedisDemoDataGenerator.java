package com.example.eip.channels;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisDemoDataGenerator extends RouteBuilder {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${eip.redis.demo.data.generator.enabled:true}")
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

                redisTemplate.convertAndSend("eip.orders.notifications", json);
                exchange.getIn().setBody(json);
            })
            .log("Redis Pub/Sub → published notification: ${body}");
    }
}
