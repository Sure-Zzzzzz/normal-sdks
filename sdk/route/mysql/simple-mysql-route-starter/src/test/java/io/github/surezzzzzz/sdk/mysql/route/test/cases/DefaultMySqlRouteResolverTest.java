package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.resolver.DefaultMySqlRouteResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MySQL Route 默认解析器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultMySqlRouteResolverTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 默认解析器测试");
    }

    @Test
    public void shouldUsePriorityThenDeclarationOrder() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        SimpleMysqlRouteProperties.RouteRule first = rule("test_*", "test-ops-a", 100);
        SimpleMysqlRouteProperties.RouteRule second = rule("test_order", "test-audit-a", 200);
        properties.getRules().add(first);
        properties.getRules().add(second);

        DefaultMySqlRouteResolver resolver = new DefaultMySqlRouteResolver(properties, new MySqlRoutePatternMatcher());

        assertEquals("test-audit-a", resolver.resolve("test_order"));
    }

    @Test
    public void shouldKeepDeclarationOrderAtSamePriority() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        properties.getRules().add(rule("test_*", "test-ops-a", 100));
        properties.getRules().add(rule("test_order", "test-audit-a", 100));

        DefaultMySqlRouteResolver resolver = new DefaultMySqlRouteResolver(properties, new MySqlRoutePatternMatcher());

        assertEquals("test-ops-a", resolver.resolve("test_order"));
    }

    @Test
    public void shouldFailWithoutImplicitFallback() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        DefaultMySqlRouteResolver resolver = new DefaultMySqlRouteResolver(properties, new MySqlRoutePatternMatcher());

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> resolver.resolve("test_unknown"));
        assertEquals(ErrorCode.ROUTE_NOT_FOUND, exception.getCode());
        SimpleMysqlRouteException blank = assertThrows(SimpleMysqlRouteException.class,
                () -> resolver.resolve("  "));
        assertEquals(ErrorCode.ROUTE_KEY_INVALID, blank.getCode());
    }

    private SimpleMysqlRouteProperties.RouteRule rule(String pattern, String datasourceKey, int priority) {
        SimpleMysqlRouteProperties.RouteRule rule = new SimpleMysqlRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setDatasourceKey(datasourceKey);
        rule.setMatchType("wildcard");
        rule.setPriority(priority);
        return rule;
    }
}
