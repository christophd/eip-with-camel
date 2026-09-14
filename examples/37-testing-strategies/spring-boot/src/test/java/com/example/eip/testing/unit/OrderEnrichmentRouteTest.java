package com.example.eip.testing.unit;

import java.util.Map;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.eip.testing.InventoryService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@CamelSpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderEnrichmentRouteTest {

    @Autowired
    CamelContext camelContext;

    @Autowired
    ProducerTemplate producer;

    @MockitoBean(name = "inventoryService")
    InventoryService inventoryService;

    // @UseAdviceWith leaves the context stopped, and it is adviceWith that arms
    // it to be started -- calling start() on its own does not hold. So the advice
    // has to be reapplied per test method, which in turn needs a fresh Spring
    // context each method: reusing the cached one would stack another mock send
    // onto the same route and inflate the expected counts.
    @BeforeEach
    void setup() throws Exception {
        AdviceWith.adviceWith(camelContext, "kafka-order-filter", route -> {
            route.replaceFromWith("direct:test-kafka-stub");
        });
        AdviceWith.adviceWith(camelContext, "enriched-output-handler", route -> {
            route.weaveAddLast().to("mock:enriched");
        });
        camelContext.start();

        MockEndpoint.resetMocks(camelContext);

        when(inventoryService.checkStock(any())).thenReturn(Map.of(
            "order_id", 3001,
            "item_sku", "ELEC-TV-55",
            "warehouse", "WAREHOUSE-MOCK",
            "stock_available", 99,
            "weight_kg", 15.0
        ));
    }

    @Test
    void orderIsEnrichedWithInventoryData() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:enriched", MockEndpoint.class);
        mock.expectedMessageCount(1);

        String order = """
            {"order_id": 3001, "item_sku": "ELEC-TV-55", "amount": 599.99}
            """;
        producer.sendBody("direct:enrich-order", order);

        mock.assertIsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(body);
        assertTrue(body.contains("WAREHOUSE-MOCK"));
        assertTrue(body.contains("99"));
    }
}
