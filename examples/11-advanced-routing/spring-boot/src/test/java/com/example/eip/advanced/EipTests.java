package com.example.eip.advanced;

import java.time.Duration;

import com.example.eip.advanced.config.EipInfraSetup;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.junit.jupiter.spring.CitrusSpringSupport;
import org.citrusframework.kafka.endpoint.KafkaMessageFilter;
import org.citrusframework.kafka.message.KafkaMessageHeaders;
import org.citrusframework.spi.Resources;
import org.citrusframework.spring.config.CitrusSpringConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.citrusframework.kafka.endpoint.selector.KafkaMessageByHeaderSelector.kafkaHeaderEquals;

@SpringBootTest(classes = AdvancedRoutingApplication.class)
@CamelSpringBootTest
@CitrusSpringSupport
@ContextConfiguration(classes = { EipInfraSetup.class, CitrusSpringConfig.class })
class EipTests implements EipTestSupport {

    @Autowired
    CamelContext camelContext;

    @Nested
    class WireTapTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldProcessOrderAndSendAuditCopy() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", "150.00")
                    .variable("country", "US")
                    .variable("priority", "EXPRESS")
            );

            t.given(waitForCamelRouteStarted("wire-tap-main", camelContext));

            t.when(
                send()
                    .endpoint("kafka:eip.orders.processing")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
            );

            t.then(sleep().seconds(5));

            t.then(
                camel().camelContext(camelContext)
                    .controlBus()
                    .route("wire-tap-main")
                    .status()
                    .result(ServiceStatus.Started)
            );

            t.then(
                camel().camelContext(camelContext)
                    .controlBus()
                    .route("wire-tap-audit")
                    .status()
                    .result(ServiceStatus.Started)
            );
        }
    }

    @Nested
    class DynamicRouterTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldRouteOrderThroughAllProcessingSteps() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", "200.00")
                    .variable("country", "US")
                    .variable("priority", "STANDARD")
            );

            t.given(waitForCamelRouteStarted("dynamic-router-demo", camelContext));

            t.when(
                send()
                    .endpoint("kafka:eip.orders.placed")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("order-id", "${id}")
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 10)
                    .autoSleep(Duration.ofSeconds(1))
                    .actions(
                        receive()
                            .selector(KafkaMessageFilter.kafkaMessageFilter()
                                    .eventLookbackWindow(Duration.ofSeconds(10))
                                    .kafkaMessageSelector(kafkaHeaderEquals("order-id", "${id}"))
                                    .build())
                            .endpoint("kafka:eip.orders.dynamic-routed?consumerGroup=citrus-dynamic-routed-group")
                            .message()
                            .body(Resources.create("templates/dynamic-routed-order.json"))
                            .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
                    )
            );
        }
    }

    @Nested
    class ResequencerTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldResequenceOutOfOrderMessages() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", "50.00")
                    .variable("country", "US")
                    .variable("priority", "STANDARD")
            );

            t.given(waitForCamelRouteStarted("batch-resequencer", camelContext));

            t.given(
                print().message("Sending 3 messages with out-of-order sequence numbers: 3, 1, 2")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.orders.sequenced")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("order-id", "${id}")
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
                    .header("sequenceNumber", 3)
            );

            t.when(
                send()
                    .endpoint("kafka:eip.orders.sequenced")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("order-id", "${id}")
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
                    .header("sequenceNumber", 1)
            );

            t.when(
                send()
                    .endpoint("kafka:eip.orders.sequenced")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("order-id", "${id}")
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
                    .header("sequenceNumber", 2)
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 10)
                    .autoSleep(Duration.ofSeconds(1))
                    .actions(
                        receive()
                            .endpoint("kafka:eip.orders.resequenced?consumerGroup=citrus-resequenced-group")
                            .message()
                            .body(Resources.create("templates/order.json"))
                    )
            );
        }
    }

    @Nested
    class ComposedMessageProcessorTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldSplitProcessAndAggregateLineItems() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
            );

            t.given(waitForCamelRouteStarted("composed-message-processor", camelContext));

            t.when(
                send()
                    .endpoint("kafka:eip.orders.composed")
                    .message()
                    .body(Resources.create("templates/composed-order.json"))
                    .header("order-id", "${id}")
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 10)
                    .autoSleep(Duration.ofSeconds(1))
                    .actions(
                        receive()
                            .selector(KafkaMessageFilter.kafkaMessageFilter()
                                    .eventLookbackWindow(Duration.ofSeconds(10))
                                    .kafkaMessageSelector(kafkaHeaderEquals("order-id", "${id}"))
                                    .build())
                            .endpoint("kafka:eip.orders.enriched?consumerGroup=citrus-enriched-group")
                            .message()
                            .body(Resources.create("templates/enriched-order.json"))
                    )
            );
        }
    }

    @Nested
    class LoadBalancerTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldDistributeOrdersAcrossFulfillmentCenters() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", "75.00")
                    .variable("country", "US")
                    .variable("priority", "STANDARD")
            );

            t.given(waitForCamelRouteStarted("load-balancer-demo", camelContext));

            t.when(
                send()
                    .endpoint("kafka:eip.orders.loadbalanced")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header(KafkaMessageHeaders.MESSAGE_KEY, "${id}")
            );

            t.then(sleep().seconds(5));

            t.then(
                camel().camelContext(camelContext)
                    .controlBus()
                    .route("load-balancer-demo")
                    .status()
                    .result(ServiceStatus.Started)
            );
        }
    }
}
