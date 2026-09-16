package com.example.eip.dev;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The test that continuous testing re-runs on every save.
 *
 * <p>Start dev mode, press {@code r}, then edit
 * {@link OrderPriorityRoute#PRIORITY_THRESHOLD} and watch this go red without
 * ever leaving the terminal.
 *
 * <p>It drives {@code direct:classify-priority} rather than the Kafka consumer,
 * so it needs no broker and finishes in milliseconds -- which is what makes it
 * tolerable to run on every keystroke.
 */
@QuarkusTest
class OrderPriorityRouteTest {

    @Inject
    CamelContext context;

    @Inject
    ProducerTemplate producer;

    MockEndpoint classified;

    @BeforeEach
    void setUp() {
        classified = context.getEndpoint("mock:classified", MockEndpoint.class);
        classified.reset();
    }

    @Test
    void ordersAtOrAboveTheThresholdAreExpedited() throws Exception {
        classified.expectedMessageCount(1);

        producer.sendBody("direct:classify-priority",
            newOrder("ORD-1", OrderPriorityRoute.PRIORITY_THRESHOLD));

        classified.assertIsSatisfied();
        assertEquals("EXPEDITED", priorityOf(classified));
    }

    @Test
    void ordersBelowTheThresholdAreStandard() throws Exception {
        classified.expectedMessageCount(1);

        producer.sendBody("direct:classify-priority",
            newOrder("ORD-2", OrderPriorityRoute.PRIORITY_THRESHOLD - 0.01));

        classified.assertIsSatisfied();
        assertEquals("STANDARD", priorityOf(classified));
    }

    private static Map<String, Object> newOrder(String id, double amount) {
        return new java.util.HashMap<>(Map.of("order_id", id, "amount", amount));
    }

    @SuppressWarnings("unchecked")
    private static String priorityOf(MockEndpoint mock) {
        Map<String, Object> body = mock.getExchanges().get(0).getIn().getBody(Map.class);
        return String.valueOf(body.get("priority"));
    }
}
