package com.example.eip.aimcp;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class OrderAssistantRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        rest("/api/assistant")
            .post("/chat")
            .consumes("application/json")
            .produces("application/json")
            .to("direct:assistant-chat");

        from("direct:assistant-chat")
            .routeId("assistant-chat")
            .log("Assistant query: ${body}")
            // The agent takes the system prompt as its own header and the user
            // query as the body, rather than the two being concatenated into a
            // single chat prompt.
            .setHeader("CamelLangChain4jAgentSystemMessage", constant(
                "You are a helpful shipping order assistant. You can look up order statuses "
                + "using the available tools. Be concise and helpful."))
            // tags=shipping selects the ai-tool routes this agent may call.
            .to("langchain4j-agent:assistant?agent=#assistantAgent&tags=shipping")
            .log("Assistant response: ${body}");
    }
}
