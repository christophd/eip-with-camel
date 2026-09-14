package com.example.eip.aimcp;

import dev.langchain4j.model.chat.ChatModel;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentWithoutMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the agent that {@code langchain4j-agent:} endpoints reference by name.
 *
 * <p>The bean name is what {@code ?agent=#assistantAgent} resolves against.
 *
 * <p>{@link AgentWithoutMemory} treats every exchange as an independent
 * conversation. For a multi-turn assistant, return an {@code AgentWithMemory}
 * and set a {@code ChatMemoryProvider} on the {@link AgentConfiguration}.
 */
@Configuration
public class AgentConfig {

    @Bean
    public Agent assistantAgent(ChatModel chatModel) {
        AgentConfiguration config = new AgentConfiguration()
            .withChatModel(chatModel);

        return new AgentWithoutMemory(config);
    }
}
