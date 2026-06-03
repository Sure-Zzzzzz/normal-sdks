package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.listener.ElasticsearchSearchMetricsListener;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.SimpleElasticsearchSearchMetricsTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试 me 标签兜底逻辑
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchMetricsTestApplication.class)
class SimpleElasticsearchSearchMetricsMeTagTest {

    @Autowired
    private ElasticsearchSearchMetricsListener listener;

    @Test
    @DisplayName("spring.application.name 有值 → me 取 application name")
    void testMeFromApplicationName() throws Exception {
        log.info("========== 测试：me 取 spring.application.name ==========");

        String me = readMeField(listener);
        log.info("me={}", me);
        assertEquals("es-search-metrics-test", me, "me 应等于 spring.application.name");
        log.info("✓ me 取 spring.application.name 验证通过");
    }

    private String readMeField(Object target) throws Exception {
        Field field = ElasticsearchSearchMetricsListener.class.getDeclaredField("me");
        field.setAccessible(true);
        return (String) field.get(target);
    }
}