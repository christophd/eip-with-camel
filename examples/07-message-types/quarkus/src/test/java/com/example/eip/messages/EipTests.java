package com.example.eip.messages;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.camel.endpoint.CamelEndpointBuilder;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.Resources;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@CitrusSupport
class EipTests implements EipTestSupport {

    @CitrusResource
    TestCaseRunner t;

    @Inject
    @BindToRegistry
    CamelContext camelContext;

    @Nested
    class CommandMessageTest {

        @Test
        public void shouldPublishCommandToKafka() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", 99.95)
            );

            t.given(waitForCamelRouteStarted("command-message-consumer", camelContext));

            t.given(
                print().message("Send command message via direct → Kafka")
            );

            t.when(
                camel()
                    .send()
                    .endpoint(new CamelEndpointBuilder()
                            .endpointUri("direct:send-command")
                            .camelContext(camelContext)
                            .build())
                    .fork(true)
                    .message()
                    .body(Resources.create("templates/command.json"))
                    .header("kafka.KEY", "PAY-${id}")
            );

            t.then(
                receive()
                    .endpoint("kafka:eip.commands.process-payment?consumerGroup=citrus-command-group")
                    .message()
                    .body(Resources.create("templates/command.json"))
            );
        }
    }

    @Nested
    class DocumentMessageTest {

        @Test
        public void shouldPublishDocumentToKafka() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", 149.99)
            );

            t.given(waitForCamelRouteStarted("document-message-consumer", camelContext));

            t.given(
                print().message("Send document message via direct → Kafka")
            );

            t.when(
                camel()
                    .send()
                    .endpoint(new CamelEndpointBuilder()
                            .endpointUri("direct:send-document")
                            .camelContext(camelContext)
                            .build())
                    .fork(true)
                    .message()
                    .body(Resources.create("templates/document.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(
                receive()
                    .endpoint("kafka:eip.documents.orders?consumerGroup=citrus-document-group")
                    .message()
                    .body(Resources.create("templates/document.json"))
            );
        }
    }

    @Nested
    class EventMessageTest {

        @Test
        public void shouldPublishEventToKafka() {
            t.given(
                createVariables()
                    .variable("id", "citrus:randomNumber(4)")
                    .variable("amount", 75.50)
            );

            t.given(waitForCamelRouteStarted("event-message-consumer", camelContext));

            t.given(
                print().message("Send event message via direct → Kafka")
            );

            t.when(
                camel()
                    .send()
                    .endpoint(new CamelEndpointBuilder()
                            .endpointUri("direct:send-event")
                            .camelContext(camelContext)
                            .build())
                    .fork(true)
                    .message()
                    .body(Resources.create("templates/event.json"))
                    .header("kafka.KEY", "${id}")
            );

            t.then(
                receive()
                    .endpoint("kafka:eip.events.orders?consumerGroup=citrus-event-group")
                    .message()
                    .body(Resources.create("templates/event.json"))
            );
        }
    }
}
