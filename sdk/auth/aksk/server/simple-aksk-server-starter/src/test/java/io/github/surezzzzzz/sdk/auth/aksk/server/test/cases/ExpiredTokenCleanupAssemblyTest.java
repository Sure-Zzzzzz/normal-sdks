package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过期 Token 清理任务装配测试。
 * <p>
 * 验证 cleanup.enable 条件装配：默认（不配置）装配任务 bean；
 * 显式 false 时任务 bean 不存在。同时验证定时清理任务装配形态下
 * 应用正常启动（@EnableScheduling 生效、锁依赖在场）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.auth.aksk.server.cleanup.enable=false"
})
class ExpiredTokenCleanupAssemblyTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testCleanupDisabledDoesNotAssembleSchedulerBean() {
        assertFalse(applicationContext.containsBean("expiredTokenCleanupScheduler"),
                "cleanup.enable=false 时清理任务 bean 不应装配");
        log.info("✓ cleanup.enable=false：ExpiredTokenCleanupScheduler 不装配");
    }

    @SpringBootTest(
            classes = SimpleAkskServerTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
    )
    static class EnabledByDefaultNested {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void testCleanupEnabledByDefaultAssemblesSchedulerBean() {
            assertTrue(applicationContext.containsBean("expiredTokenCleanupScheduler"),
                    "默认配置下清理任务 bean 应装配（cleanup.enable 缺省 true）");
            log.info("✓ 默认配置：ExpiredTokenCleanupScheduler 装配，@EnableScheduling 与锁依赖就位");
        }
    }
}
