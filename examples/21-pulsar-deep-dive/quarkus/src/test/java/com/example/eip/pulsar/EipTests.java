package com.example.eip.pulsar;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.camel.dsl.CamelSupport;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;

@QuarkusTest
@CitrusSupport
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class EipTests implements EipTestSupport {

    @CitrusResource
    TestCaseRunner t;

    @Inject
    @BindToRegistry
    CamelContext camelContext;

    @Nested
    @Order(1)
    class SharedSubscriptionTest {

        @Test
        public void shouldProcessOrderWithSharedSubscription() {
            t.given(waitForCamelRouteStarted("pulsar-shared-consumer", camelContext));

            t.when(
                camel()
                    .send()
                    .endpoint(CamelSupport.camel().endpoints()
                            .pulsar("persistent://public/default/eip.orders.placed")
                            .serviceUrl("pulsar://localhost:6650")
                            .producerName("citrus-shared-sub-test")::getRawUri)
                    .message()
                    .body("{\"order_id\": 2001, \"customer_id\": \"C-001\", \"item_sku\": \"SKU-P1\", \"quantity\": 2, \"amount\": 49.99}")
            );

            t.then(
                assertProcessedExchanges("pulsar-order-processor", it -> it >= 1, camelContext)
            );
        }
    }

    @Nested
    @Order(2)
    class KeySharedSubscriptionTest {

        @Test
        public void shouldProcessKeyedOrderWithKeySharedSubscription() {
            t.given(waitForCamelRouteStarted("pulsar-key-shared-consumer", camelContext));

            t.when(
                camel()
                    .send()
                    .endpoint(CamelSupport.camel().endpoints()
                            .pulsar("persistent://public/default/eip.orders.keyed")
                            .serviceUrl("pulsar://localhost:6650")
                            .producerName("citrus-key-shared-test")::getRawUri)
                    .message()
                    .header("pulsar.producer.message.key", "order-3001")
                    .body("{\"order_id\": 3001, \"customer_id\": \"C-001\", \"item_sku\": \"SKU-P2\", \"quantity\": 3, \"status\": \"placed\"}")
            );

            t.then(
                assertProcessedExchanges("pulsar-keyed-order-processor", it -> it >= 1, camelContext)
            );
        }
    }

    @Nested
    @Order(3)
    class DeadLetterTopicTest {

        @Test
        public void shouldProcessValidPaymentOrder() {
            // quantity=2 does NOT trigger the simulated failure (only quantity=1 does)
            t.given(waitForCamelRouteStarted("pulsar-dlt-consumer", camelContext));

            t.when(
                camel()
                    .send()
                    .endpoint(CamelSupport.camel().endpoints()
                            .pulsar("persistent://public/default/eip.orders.payments")
                            .serviceUrl("pulsar://localhost:6650")
                            .producerName("citrus-dlt-test")::getRawUri)
                    .message()
                    .body("{\"order_id\": 4002, \"customer_id\": \"C-002\", \"item_sku\": \"SKU-P3\", \"quantity\": 2, \"amount\": 39.98}")
            );

            t.then(
                assertProcessedExchanges("pulsar-dlt-consumer", it -> it >= 1, camelContext)
            );
        }

        @Test
        public void shouldMonitorDeadLetterTopic() {
            t.given(waitForCamelRouteStarted("pulsar-dlt-monitor", camelContext));

            t.then(
                assertProcessedExchanges("pulsar-dlt-monitor", it -> it >= 0, camelContext)
            );
        }
    }
}
