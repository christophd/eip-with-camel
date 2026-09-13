package com.example.eip.channels;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub — demonstrates Redis as a messaging channel alongside
 * Kafka (point-to-point) and Pulsar (pub-sub). Redis Pub/Sub is fire-and-forget:
 * messages are NOT persisted, so subscribers only receive messages published
 * while they are connected. Good for real-time notifications where durability
 * is not required.
 */
@Component
public class RedisChannelRoute extends RouteBuilder {

    @Autowired
    private RedisMessageListenerContainer redisListenerContainer;

    @Override
    public void configure() {
        from("timer:redis-subscriber-start?repeatCount=1&delay=2000")
            .routeId("redis-pubsub-subscriber")
            .process(exchange -> {
                MessageListener listener = (Message message, byte[] pattern) ->
                    log.info("Redis Pub/Sub ← received: {}", new String(message.getBody()));
                redisListenerContainer.addMessageListener(listener,
                    new ChannelTopic("eip.orders.notifications"));
            })
            .log("Redis Pub/Sub subscriber started on channel: eip.orders.notifications");
    }
}
