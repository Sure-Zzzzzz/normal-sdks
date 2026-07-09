package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ElasticsearchCompatibilityHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class ElasticsearchCompatibilityHelperTest {

    @Test
    @DisplayName("totalHits 对象含 public value 字段时直接返回 value")
    void testExtractTotalHitsValueFromPublicField() throws Exception {
        TestTotalHits totalHits = new TestTotalHits(123L);

        long result = invokeExtractTotalHitsValue(totalHits);

        log.info("======================================");
        log.info("测试: totalHits public value 字段");
        log.info("输入: {}", totalHits.value);
        log.info("输出: {}", result);
        log.info("======================================");

        assertEquals(123L, result, "应直接读取 public value 字段");
    }

    @Test
    @DisplayName("totalHits 对象没有 value 字段时返回 0")
    void testExtractTotalHitsValueFallbackZero() throws Exception {
        Object totalHits = new Object();

        long result = invokeExtractTotalHitsValue(totalHits);

        log.info("======================================");
        log.info("测试: totalHits 无 value 字段兜底");
        log.info("输出: {}", result);
        log.info("======================================");

        assertEquals(0L, result, "无 value 字段时应返回 0");
    }

    private long invokeExtractTotalHitsValue(Object totalHits) throws Exception {
        Method method = ElasticsearchCompatibilityHelper.class
                .getDeclaredMethod("extractTotalHitsValue", Object.class);
        method.setAccessible(true);
        return (Long) method.invoke(null, totalHits);
    }

    public static class TestTotalHits {
        public final Long value;

        TestTotalHits(Long value) {
            this.value = value;
        }
    }
}
