package com.example.eip.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the OpenTelemetry SDK that the Spring Boot starter builds to
 * {@link GlobalOpenTelemetry}.
 *
 * <p>Without this, the Spring Boot application exports HTTP spans and logs but
 * no Camel route spans at all. Camel's tracer resolves its SDK by looking for a
 * single {@code OpenTelemetry} instance in the Camel registry and, failing
 * that, calling {@code GlobalOpenTelemetry.get()}. The starter keeps its SDK as
 * a Spring bean and never registers it globally, and the registry lookup does
 * not find it, so Camel silently falls back to the no-op instance and every
 * route span is discarded.
 *
 * <p>This class is a plain {@code @Configuration}, not an auto-configuration,
 * which is what makes the ordering work: Spring registers user configuration
 * before auto-configuration, so the global is set by the time Camel's tracer
 * initialises.
 */
@Configuration
public class OpenTelemetryGlobalConfig {

    private static final Logger LOG = LoggerFactory.getLogger(OpenTelemetryGlobalConfig.class);

    public OpenTelemetryGlobalConfig(OpenTelemetry openTelemetry) {
        try {
            GlobalOpenTelemetry.set(openTelemetry);
            LOG.info("Registered the Spring-managed OpenTelemetry SDK as the global instance");
        } catch (IllegalStateException e) {
            // Already set — harmless on a context restart, and in tests.
            LOG.debug("GlobalOpenTelemetry was already set; leaving it alone", e);
        }
    }
}
