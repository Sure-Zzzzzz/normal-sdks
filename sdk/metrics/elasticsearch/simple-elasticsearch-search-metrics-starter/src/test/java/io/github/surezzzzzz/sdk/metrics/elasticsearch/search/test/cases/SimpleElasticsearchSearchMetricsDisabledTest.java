package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.listener.ElasticsearchSearchMetricsListener;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.SimpleElasticsearchSearchMetricsTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 测试 enable=false 时 Listener 不注册
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchMetricsTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.metrics.elasticsearch.search.enable=false"
})
class SimpleElasticsearchSearchMetricsDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("enable=false → ElasticsearchSearchMetricsListener 不注册")
    void testListenerNotRegisteredWhenDisabled() {
        log.info("========== 测试：enable=false 时 Listener 不注册 ==========");

        boolean hasListener = applicationContext.getBeansOfType(ElasticsearchSearchMetricsListener.class).size() > 0;

        log.info("hasListener={}", hasListener);
        assertFalse(hasListener, "enable=false 时 ElasticsearchSearchMetricsListener 不应注册");

        log.info("✓ enable=false 时 Listener 不注册验证通过");
    }
}
