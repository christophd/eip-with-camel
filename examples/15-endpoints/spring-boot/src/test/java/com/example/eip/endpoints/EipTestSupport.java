package com.example.eip.endpoints;

import java.time.Duration;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.citrusframework.TestActionBuilder;
import org.citrusframework.dsl.TestActionSupport;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.exceptions.ValidationException;

public interface EipTestSupport extends TestActionSupport {

    default TestActionBuilder<?> waitForCamelRouteStarted(String routeId, CamelContext camelContext) {
        return repeatOnError()
                .until((i, context) -> i > 20)
                .autoSleep(Duration.ofSeconds(1))
                .actions(
                    camel().camelContext(camelContext)
                            .controlBus()
                            .route(routeId)
                            .status()
                            .result(ServiceStatus.Started)
                            .description("Waiting for Camel route '%s' to be started ...".formatted(routeId)),
                    sleep().seconds(5)
                );
    }

    default TestActionBuilder<?> assertProcessedExchanges(String routeId, long expected, CamelContext camelContext) {
        return assertProcessedExchanges(routeId, it -> it == expected, camelContext);
    }

    default TestActionBuilder<?> assertProcessedExchanges(String routeId, Predicate<Long> check, CamelContext camelContext) {
        return repeatOnError()
                .until((i, context) -> i > 20)
                .autoSleep(Duration.ofSeconds(1))
                .actions(
                    context -> {
                        ManagedCamelContext managedContext = camelContext.getCamelContextExtension()
                                .getContextPlugin(ManagedCamelContext.class);

                        ManagedRouteMBean routeMBean = managedContext.getManagedRoute(routeId);

                        if (routeMBean != null) {
                            long failed = routeMBean.getExchangesFailed();

                            if (failed > 0) {
                                throw new ValidationException("Route with routeId '%s' has %d failed exchanges".formatted(routeId, failed));
                            }

                            long completed = routeMBean.getExchangesCompleted();
                            if (!check.test(completed)) {
                                throw new ValidationException("Route with routeId '%s' has %d completed exchanges and did not pass the expectations".formatted(routeId, completed));
                            }
                        } else {
                            throw new CitrusRuntimeException(String.format("Failed to get managed route statistics for routeId '%s'", routeId));
                        }
                    }
                );
    }
}
