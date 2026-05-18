package io.github.surezzzzzz.sdk.metrics.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.metrics.aksk.server.listener.TokenMetricsListener;
import io.github.surezzzzzz.sdk.metrics.aksk.server.test.SimpleAkskServerMetricsTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 metrics.enable=false 时 Listener 不注册
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerMetricsTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.metrics.aksk.server.enable=false"
})
public class SimpleAkskServerMetricsDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testTokenMetricsListenerNotRegisteredWhenDisabled() {
        log.info("========== 测试：enable=false 时 TokenMetricsListener 不注册 ==========");

        boolean hasListener = applicationContext.getBeansOfType(TokenMetricsListener.class).size() > 0;
        assertFalse(hasListener, "TokenMetricsListener should not be registered when enable=false");

        log.info("testTokenMetricsListenerNotRegisteredWhenDisabled passed");
    }
}
