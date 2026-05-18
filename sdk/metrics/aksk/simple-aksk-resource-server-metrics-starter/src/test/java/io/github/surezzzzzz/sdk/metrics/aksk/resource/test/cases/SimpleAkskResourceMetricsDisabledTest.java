package io.github.surezzzzzz.sdk.metrics.aksk.resource.test.cases;

import io.github.surezzzzzz.sdk.metrics.aksk.resource.listener.AkskAccessMetricsListener;
import io.github.surezzzzzz.sdk.metrics.aksk.resource.test.SimpleAkskResourceMetricsTestApplication;
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
@SpringBootTest(classes = SimpleAkskResourceMetricsTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.metrics.aksk.resource.enable=false"
})
public class SimpleAkskResourceMetricsDisabledTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void testAccessMetricsListenerNotRegisteredWhenDisabled() {
        log.info("========== 测试：enable=false 时 AkskAccessMetricsListener 不注册 ==========");

        boolean hasListener = applicationContext.getBeansOfType(AkskAccessMetricsListener.class).size() > 0;
        assertFalse(hasListener, "AkskAccessMetricsListener should not be registered when enable=false");

        log.info("testAccessMetricsListenerNotRegisteredWhenDisabled passed");
    }
}