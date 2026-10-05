package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceAutoConfiguration;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.JacksonPersistencePayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistencePayloadCodec;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceRestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协议统计、JSON 隔离和启用条件的独立合同，不把模拟响应当真实 ES 验收。
 */
@Slf4j
class PersistenceContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "9223372036854775808"})
    void invalidStatisticsFailClosed(String value) {
        Number number = value.contains(".") ? Double.parseDouble(value) : new BigInteger(value);
        assertThrows(PersistenceExecutionException.class, () -> PersistenceRestHelper.number(Map.of("count", number), "count"));
    }

    @Test
    void nonNumericMissingAndInfiniteStatisticsFailClosed() {
        assertThrows(PersistenceExecutionException.class, () -> PersistenceRestHelper.number(Map.of("count", "1"), "count"));
        assertThrows(PersistenceExecutionException.class, () -> PersistenceRestHelper.number(Map.of(), "count"));
        assertThrows(PersistenceExecutionException.class, () -> PersistenceRestHelper.number(Map.of("count", Double.NaN), "count"));
        assertThrows(PersistenceExecutionException.class, () -> PersistenceRestHelper.number(Map.of("count", Double.POSITIVE_INFINITY), "count"));
        assertEquals(Long.MAX_VALUE, PersistenceRestHelper.number(Map.of("count", Long.MAX_VALUE), "count"));
    }

    @Test
    void disabledConfigurationDoesNotCreateBusinessBeans() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SimpleElasticsearchPersistenceAutoConfiguration.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(0, context.getBeansOfType(PersistencePayloadCodec.class).size());
                });
    }

    @Test
    void enabledWithoutRouteFailsInsteadOfDirectConnection() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SimpleElasticsearchPersistenceAutoConfiguration.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.elasticsearch.persistence.enable=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void jsonBoundaryIsIndependentAndRejectsMalformedResponses() {
        PersistencePayloadCodec codec = new JacksonPersistencePayloadCodec();
        assertEquals(Map.of("amount", 1), codec.decode(codec.encode(Map.of("amount", 1))));
        assertThrows(RuntimeException.class, () -> codec.decode("not-json"));
        assertThrows(RuntimeException.class, () -> codec.decode("[]"));
        assertNull(PersistenceRestHelper.protocol().getCause());
    }
}
