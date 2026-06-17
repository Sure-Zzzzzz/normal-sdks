package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.OperatorStrategyRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OperatorStrategyRegistry 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class OperatorStrategyRegistryTest {

    @Autowired
    private OperatorStrategyRegistry registry;

    @Test
    @DisplayName("所有 QueryOperator 均可 resolve（新增 3 个后共 21 个）")
    void testAllOperatorsResolved() {
        int count = 0;
        for (QueryOperator op : QueryOperator.values()) {
            OperatorStrategy strategy = registry.resolve(op);
            assertNotNull(strategy, "操作符 " + op + " 应能解析策略");
            count++;
        }
        log.info("共 {} 个操作符全部可解析", count);
        assertEquals(21, count, "应有 21 个操作符（原有 18 + 新增 NOT_PREFIX/NOT_SUFFIX/NOT_REGEX）");
    }

    @Test
    @DisplayName("resolve - NOT_PREFIX 策略可正常解析")
    void testResolveNotPrefix() {
        OperatorStrategy strategy = registry.resolve(QueryOperator.NOT_PREFIX);
        assertNotNull(strategy, "NOT_PREFIX 策略不应为 null");
        log.info("NOT_PREFIX 策略: {}", strategy.getClass().getSimpleName());
        assertEquals("NotPrefixOperatorStrategy", strategy.getClass().getSimpleName());
    }

    @Test
    @DisplayName("resolve - NOT_SUFFIX 策略可正常解析")
    void testResolveNotSuffix() {
        OperatorStrategy strategy = registry.resolve(QueryOperator.NOT_SUFFIX);
        assertNotNull(strategy, "NOT_SUFFIX 策略不应为 null");
        log.info("NOT_SUFFIX 策略: {}", strategy.getClass().getSimpleName());
        assertEquals("NotSuffixOperatorStrategy", strategy.getClass().getSimpleName());
    }

    @Test
    @DisplayName("resolve - NOT_REGEX 策略可正常解析")
    void testResolveNotRegex() {
        OperatorStrategy strategy = registry.resolve(QueryOperator.NOT_REGEX);
        assertNotNull(strategy, "NOT_REGEX 策略不应为 null");
        log.info("NOT_REGEX 策略: {}", strategy.getClass().getSimpleName());
        assertEquals("NotRegexOperatorStrategy", strategy.getClass().getSimpleName());
    }

    @Test
    @DisplayName("resolve - 原有 18 个策略仍然可用")
    void testResolveExistingOperators() {
        for (QueryOperator op : QueryOperator.values()) {
            if (op == QueryOperator.NOT_PREFIX || op == QueryOperator.NOT_SUFFIX
                    || op == QueryOperator.NOT_REGEX) {
                continue;
            }
            OperatorStrategy strategy = registry.resolve(op);
            assertNotNull(strategy, "原有操作符 " + op + " 应能解析策略");
        }
    }
}