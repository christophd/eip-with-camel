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

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentGatewayUnitTest {

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
    void resetMocks() throws Exception {
        AdviceWith.adviceWith(camelContext, "kafka-order-filter", route -> {
            route.replaceFromWith("direct:test-kafka-stub");
        });
        AdviceWith.adviceWith(camelContext, "mock-gateway", route -> {
            route.weaveAddLast().to("mock:gateway-output");
        });
        camelContext.start();

        MockEndpoint.resetMocks(camelContext);
    }

    @Test
    void mockGatewayReturnsApproval() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:gateway-output", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String payment = """
            {"order_id": 4001, "amount": 99.99, "card_token": "tok_test_visa"}
            """;
        String response = producer.requestBody("direct:mock-gateway", payment, String.class);

        mock.assertIsSatisfied();
        assertTrue(response.contains("APPROVED"));
        assertTrue(response.contains("MOCK"));
    }

    @Test
    void productionGatewayReturnsForwarded() throws Exception {
        String payment = """
            {"order_id": 4002, "amount": 199.99, "card_token": "tok_test_mc"}
            """;
        String response = producer.requestBody("direct:production-gateway", payment, String.class);

        assertTrue(response.contains("FORWARDED"));
        assertTrue(response.contains("PRODUCTION"));
    }
}
