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
 * 测试 me 标签兜底逻辑 - unknown
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchMetricsTestApplication.class)
@TestPropertySource(properties = {
        "spring.application.name=",
        "io.github.surezzzzzz.sdk.metrics.elasticsearch.search.me="
})
class SimpleElasticsearchSearchMetricsMeUnknownTest {

    @Autowired
    private ElasticsearchSearchMetricsListener listener;

    @Test
    @DisplayName("两者均无 → me=unknown")
    void testMeUnknown() throws Exception {
        log.info("========== 测试：me 为 unknown ==========");

        Field field = ElasticsearchSearchMetricsListener.class.getDeclaredField("me");
        field.setAccessible(true);
        String me = (String) field.get(listener);

        log.info("me={}", me);
        assertEquals("unknown", me, "me 应为 unknown");
        log.info("✓ me=unknown 验证通过");
    }
}