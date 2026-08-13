package com.example.eip.metadata;

import java.time.Duration;

import com.example.eip.metadata.config.EipInfraSetup;
import org.apache.camel.CamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.junit.jupiter.spring.CitrusSpringSupport;
import org.citrusframework.spi.Resources;
import org.citrusframework.spring.config.CitrusSpringConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = MessageMetadataApplication.class)
@CamelSpringBootTest
@CitrusSpringSupport
@ContextConfiguration(classes = { EipInfraSetup.class, CitrusSpringConfig.class })
class EipTests implements EipTestSupport {

    @Autowired
    CamelContext camelContext;

    @Nested
    class CorrelationIdTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldEnrichOrderWithCorrelationId() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", 99.95)
            );

            t.given(waitForCamelRouteStarted("correlation-id-producer", camelContext));

            t.given(
                print().message("Send order to Kafka and verify correlation ID enrichment")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.metadata.orders")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 25)
                    .autoSleep(Duration.ofMillis(500))
                    .actions(
                        receive()
                            .endpoint("kafka:eip.metadata.orders.correlated?consumerGroup=citrus-correlated-group")
                            .message()
                            .body(Resources.create("templates/order-correlated.json"))
                    )
            );
        }
    }

    @Nested
    class FormatIndicatorTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldRouteJsonFormattedMessage() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", 149.99)
            );

            t.given(waitForCamelRouteStarted("format-indicator-consumer", camelContext));

            t.given(
                print().message("Send order to Kafka and verify format indicator routing")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.metadata.orders")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 25)
                    .autoSleep(Duration.ofMillis(500))
                    .actions(
                        receive()
                            .endpoint("kafka:eip.metadata.orders.processed?consumerGroup=citrus-processed-group")
                            .message()
                            .body(Resources.create("templates/order.json"))
                    )
            );
        }
    }

    @Nested
    class MessageExpirationTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldProcessNonExpiredMessage() {
            long futureExpiry = System.currentTimeMillis() + 60_000;

            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", 75.50)
            );

            t.given(waitForCamelRouteStarted("message-expiration-consumer", camelContext));

            t.given(
                print().message("Send order directly to expiring topic with valid TTL and verify fulfillment")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.metadata.orders.expiring")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("kafka.KEY", "${id}")
                    .header("messageCreatedAt", System.currentTimeMillis())
                    .header("messageExpiresAt", futureExpiry)
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 25)
                    .autoSleep(Duration.ofMillis(500))
                    .actions(
                        receive()
                            .endpoint("kafka:eip.metadata.orders.fulfilled?consumerGroup=citrus-fulfilled-group")
                            .message()
                    )
            );
        }
    }

    @Nested
    class MessageSequenceTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldSplitAndReassembleBulkOrder() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
            );

            t.given(waitForCamelRouteStarted("message-sequence-aggregator", camelContext));

            t.given(
                print().message("Send bulk order to Kafka and verify split/aggregate reassembly")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.metadata.bulk-orders")
                    .message()
                    .body(Resources.create("templates/bulk-order.json"))
                    .header("kafka.KEY", "BULK-${id}")
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 25)
                    .autoSleep(Duration.ofMillis(500))
                    .actions(
                        receive()
                            .endpoint("kafka:eip.metadata.orders.reassembled?consumerGroup=citrus-reassembled-group")
                            .message()
                    )
            );
        }
    }
}
