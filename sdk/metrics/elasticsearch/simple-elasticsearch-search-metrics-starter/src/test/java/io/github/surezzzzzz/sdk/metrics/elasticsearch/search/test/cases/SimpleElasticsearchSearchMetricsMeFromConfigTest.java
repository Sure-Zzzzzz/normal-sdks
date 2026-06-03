package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.listener.ElasticsearchSearchMetricsListener;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.SimpleElasticsearchSearchMetricsTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测试 me 标签兜底逻辑 - 取配置项
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchMetricsTestApplication.class)
@TestPropertySource(properties = {
        "spring.application.name=",
        "io.github.surezzzzzz.sdk.metrics.elasticsearch.search.me=user-service"
})
class SimpleElasticsearchSearchMetricsMeFromConfigTest {

    @Autowired
    private ElasticsearchSearchMetricsListener listener;

    @Test
    @DisplayName("application name 为空，me 配置有值 → me 取配置项")
    void testMeFromConfig() throws Exception {
        log.info("========== 测试：me 取配置项 ==========");

        Field field = ElasticsearchSearchMetricsListener.class.getDeclaredField("me");
        field.setAccessible(true);
        String me = (String) field.get(listener);

        log.info("me={}", me);
        assertEquals("user-service", me, "me 应等于配置项");
        log.info("✓ me 取配置项验证通过");
    }
}