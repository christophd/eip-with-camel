package com.example.eip.composed;

import java.time.Duration;

import com.example.eip.composed.config.EipInfraSetup;
import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
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

@SpringBootTest(classes = ComposedRoutingApplication.class)
@CamelSpringBootTest
@CitrusSpringSupport
@ContextConfiguration(classes = { EipInfraSetup.class, CitrusSpringConfig.class })
class EipTests implements EipTestSupport {

    @Autowired
    CamelContext camelContext;

    @Nested
    class RoutingSlipDomesticTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldRouteStandardDomesticOrder() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("country", "US")
                    .variable("hazmat", false)
            );

            t.given(waitForCamelRouteStarted("order-routing-slip", camelContext));

            t.given(
                print().message("Send standard US domestic order — slip: validate → assign-carrier")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.orders.placed")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(sleep().seconds(5));

            t.then(
                camel().camelContext(camelContext)
                    .controlBus()
                    .route("order-routing-slip")
                    .status()
                    .result(ServiceStatus.Started)
            );
        }
    }

    @Nested
    class RoutingSlipHazmatTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldRouteHazmatOrder() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("country", "US")
                    .variable("hazmat", true)
            );

            t.given(waitForCamelRouteStarted("order-routing-slip", camelContext));

            t.given(
                print().message("Send hazmat US order — slip: validate → hazmat-compliance → assign-carrier")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.orders.placed")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(sleep().seconds(5));

            t.then(
                camel().camelContext(camelContext)
                    .controlBus()
                    .route("hazmat-compliance")
                    .status()
                    .result(ServiceStatus.Started)
            );
        }
    }

    @Nested
    class RoutingSlipInternationalTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldRouteInternationalOrder() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("country", "DE")
                    .variable("hazmat", false)
            );

            t.given(waitForCamelRouteStarted("order-routing-slip", camelContext));

            t.given(
                print().message("Send international DE order — slip: validate → customs-classification → assign-carrier")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.orders.placed")
                    .message()
                    .body(Resources.create("templates/order.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(sleep().seconds(5));

            t.then(
                camel().camelContext(camelContext)
                    .controlBus()
                    .route("customs-classification")
                    .status()
                    .result(ServiceStatus.Started)
            );
        }
    }

    @Nested
    class ScatterGatherTest {

        @CitrusResource
        TestCaseRunner t;

        @Test
        public void shouldSelectBestCarrierRate() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("weight", 5.0)
                    .variable("country", "US")
            );

            t.given(waitForCamelRouteStarted("carrier-scatter-gather", camelContext));

            t.given(
                print().message("Send rate request and verify best carrier rate on output topic")
            );

            t.when(
                send()
                    .endpoint("kafka:eip.shipping.rate-requests")
                    .message()
                    .body(Resources.create("templates/rate-request.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(
                repeatOnError()
                    .until((i, context) -> i > 25)
                    .autoSleep(Duration.ofMillis(500))
                    .actions(
                        receive()
                            .endpoint("kafka:eip.shipping.best-rate?consumerGroup=citrus-best-rate-group")
                            .message()
                    )
            );
        }
    }
}
