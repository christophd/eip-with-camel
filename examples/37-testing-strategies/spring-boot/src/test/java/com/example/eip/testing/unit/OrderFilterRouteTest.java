package com.example.eip.testing.unit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderFilterRouteTest {

    @Autowired
    CamelContext camelContext;

    @Autowired
    ProducerTemplate producer;

    // @UseAdviceWith leaves the context stopped, and it is adviceWith that arms
    // it to be started -- calling start() on its own does not hold. So the advice
    // has to be reapplied per test method, which in turn needs a fresh Spring
    // context each method: reusing the cached one would stack another mock send
    // onto the same route and inflate the expected counts.
    @BeforeEach
    void adviceRoutesAndStart() throws Exception {
        AdviceWith.adviceWith(camelContext, "kafka-order-filter", route -> {
            route.replaceFromWith("direct:test-kafka-input");
        });
        AdviceWith.adviceWith(camelContext, "high-value-handler", route -> {
            route.weaveAddLast().to("mock:high-value");
        });
        camelContext.start();
        MockEndpoint.resetMocks(camelContext);
    }

    @Test
    void highValueOrderPassesFilter() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:high-value", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String order = """
            {"order_id": 2001, "amount": 250.00, "customer_id": "C-100"}
            """;
        producer.sendBody("direct:filter-order", order);

        mock.assertIsSatisfied();
    }

    @Test
    void lowValueOrderIsFiltered() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:high-value", MockEndpoint.class);
        mock.expectedMessageCount(0);

        String order = """
            {"order_id": 2002, "amount": 49.99, "customer_id": "C-101"}
            """;
        producer.sendBody("direct:filter-order", order);

        mock.assertIsSatisfied();
    }

    @Test
    void borderlineOrderPassesFilter() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:high-value", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String order = """
            {"order_id": 2003, "amount": 100.00, "customer_id": "C-102"}
            """;
        producer.sendBody("direct:filter-order", order);

        mock.assertIsSatisfied();
    }
}
