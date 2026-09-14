package com.example.eip.aimcp;

import dev.langchain4j.model.chat.ChatModel;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;

/**
 * Supplies the agent that {@code langchain4j-agent:} endpoints reference by name.
 *
 * <p>{@link AgentWithoutMemory} treats every exchange as an independent
 * conversation. For a multi-turn assistant, produce an {@code AgentWithMemory}
 * and set a {@code ChatMemoryProvider} on the {@link AgentConfiguration}.
 */
@ApplicationScoped
public class AgentProducers {

    @Produces
    @Identifier("assistantAgent")
    Agent assistantAgent(ChatModel chatModel) {
        AgentConfiguration config = new AgentConfiguration()
            .withChatModel(chatModel);

        return new AgentWithoutMemory(config);
    }
}
