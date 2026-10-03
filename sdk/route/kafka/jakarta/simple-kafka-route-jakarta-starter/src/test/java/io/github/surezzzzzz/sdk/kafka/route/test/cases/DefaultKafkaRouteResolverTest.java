package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteInputType;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteOperationType;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext;
import io.github.surezzzzzz.sdk.kafka.route.resolver.DefaultKafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 默认 Kafka 路由解析器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaRouteResolverTest {

    @Test
    public void testResolveByTopicAndDefaultDatasource() {
        DefaultKafkaRouteResolver resolver = new DefaultKafkaRouteResolver(
                KafkaRouteTestDataHelper.properties(), new KafkaRoutePatternMatcher());

        assertEquals("event", resolver.resolveDataSource(context("event.order.created")));
        assertEquals("default", resolver.resolveDataSource(context("audit.order.created")));
    }

    @Test
    public void testPriorityBeforeDeclarationOrder() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getRules().clear();
        properties.getRules().add(KafkaRouteTestDataHelper.rule("event.*", RouteMatchType.WILDCARD.getCode(), "default", 10));
        properties.getRules().add(KafkaRouteTestDataHelper.rule("event.order.created", RouteMatchType.EXACT.getCode(), "event", 1));

        DefaultKafkaRouteResolver resolver = new DefaultKafkaRouteResolver(properties, new KafkaRoutePatternMatcher());

        assertEquals("event", resolver.resolveDataSource(context("event.order.created")));
    }

    @Test
    public void testSamePriorityKeepsDeclarationOrder() {
        SimpleKafkaRouteProperties properties = KafkaRouteTestDataHelper.properties();
        properties.getRules().clear();
        properties.getRules().add(KafkaRouteTestDataHelper.rule("event.", RouteMatchType.PREFIX.getCode(), "event", 1));
        properties.getRules().add(KafkaRouteTestDataHelper.rule("event.order.created", RouteMatchType.EXACT.getCode(), "default", 1));

        DefaultKafkaRouteResolver resolver = new DefaultKafkaRouteResolver(properties, new KafkaRoutePatternMatcher());

        assertEquals("event", resolver.resolveDataSource(context("event.order.created")));
    }

    @Test
    public void testEmptyRouteInputThrowsRouteException() {
        DefaultKafkaRouteResolver resolver = new DefaultKafkaRouteResolver(
                KafkaRouteTestDataHelper.properties(), new KafkaRoutePatternMatcher());

        RouteException exception = assertThrows(RouteException.class, () -> resolver.resolveDataSource(context(" ")));
        assertEquals(ErrorCode.KAFKA_ROUTE_008, exception.getErrorCode());
    }

    private KafkaRouteContext context(String routeInput) {
        return KafkaRouteContext.builder()
                .topic(routeInput)
                .routeInput(routeInput)
                .inputType(KafkaRouteInputType.TOPIC)
                .operationType(KafkaRouteOperationType.SEND)
                .build();
    }
}
