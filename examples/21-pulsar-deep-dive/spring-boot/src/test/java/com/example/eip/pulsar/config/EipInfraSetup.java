package com.example.eip.pulsar.config;

import org.citrusframework.api.container.AfterSuite;
import org.citrusframework.api.container.BeforeSuite;
import org.citrusframework.dsl.TestActionSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.citrusframework.container.SequenceAfterSuite.Builder.afterSuite;
import static org.citrusframework.container.SequenceBeforeSuite.Builder.beforeSuite;

@Configuration
public class EipInfraSetup implements TestActionSupport {

    @Bean
    public BeforeSuite startInfra() {
        return beforeSuite().actions(
                    testcontainers().compose()
                            .up("_infra/compose.yaml")
                            .containerName("eip-pulsar-deep-dive-infra")
                            .autoRemove(false),
                    waitFor()
                            .http()
                            .url("http://localhost:8080/admin/v2/brokers/health")
                            .seconds(90)
                ).build();
    }

    @Bean
    public AfterSuite stopInfra() {
        return afterSuite().actions(
                    camel().camelContext().stop(),
                    testcontainers().compose()
                            .down()
                            .containerName("eip-pulsar-deep-dive-infra")
                ).build();
    }
}
