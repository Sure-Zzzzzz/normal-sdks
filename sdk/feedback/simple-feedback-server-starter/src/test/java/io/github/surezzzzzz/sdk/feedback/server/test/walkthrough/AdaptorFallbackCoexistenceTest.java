package io.github.surezzzzzz.sdk.feedback.server.test.walkthrough;

import io.github.surezzzzzz.sdk.feedback.server.adaptor.IamAccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.adaptor.IamSubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.fixture.ServiceTestResourceAdapterConfiguration;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication;
import io.github.surezzzzzz.sdk.iam.client.IamApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * starter 降级实现与 adaptor 聚形态共存装配测试（collaboration tag）。
 *
 * <p>钉住的让位次序不变量：两个自动配置间无显式排序注解，依赖 spring.factories 字母序
 * SimpleFeedbackIamAdaptor 先于 SimpleFeedbackServer 求值——adaptor 扫描件先注册、
 * starter 的 @ConditionalOnMissingBean 降级实现让位。任一侧自动配置改名破坏次序时，
 * 本测试以双 Bean 冲突/类型不符响亮失败（而非线上静默降级为散形态）。</p>
 *
 * <p>IAM 客户端经桩 Bean 满足 adaptor 扫描件构造注入（缺件响亮失败纪律不在此测，
 * 由 IamAdaptorSpiTest 侧覆盖）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@Tag("collaboration")
@SpringBootTest(classes = {SimpleFeedbackServerTestApplication.class,
        ServiceTestResourceAdapterConfiguration.class,
        AdaptorFallbackCoexistenceTest.IamClientStubConfiguration.class},
        properties = "io.github.surezzzzzz.sdk.cache.enabled=false")
class AdaptorFallbackCoexistenceTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("共存装配：adaptor 实现在场时降级实现让位（单 Bean 且为聚形态实现）")
    void testAdaptorOverridesFallback() {
        AccessibleApplicationProvider accessible = context.getBean(AccessibleApplicationProvider.class);
        assertNotNull(accessible);
        assertTrue(accessible instanceof IamAccessibleApplicationProvider,
                "可访问应用集应为 adaptor 聚形态实现（降级实现让位）");

        SubmitterProfileProvider profile = context.getBean(SubmitterProfileProvider.class);
        assertNotNull(profile);
        assertTrue(profile instanceof IamSubmitterProfileProvider,
                "提交人档案应为 adaptor 聚形态实现（降级实现让位）");

        int accessibleCount = context.getBeanNamesForType(AccessibleApplicationProvider.class).length;
        int profileCount = context.getBeanNamesForType(SubmitterProfileProvider.class).length;
        assertEquals(1, accessibleCount, "可访问应用集 SPI 只允许单 Bean（双 Bean=次序不变量破裂）");
        assertEquals(1, profileCount, "提交人档案 SPI 只允许单 Bean（双 Bean=次序不变量破裂）");
        log.info("输出: adaptor 聚形态实现让位降级实现（单 Bean×2，次序不变量成立）");
    }

    /**
     * IAM 客户端桩（仅满足构造注入，不出站）。
     */
    @TestConfiguration
    static class IamClientStubConfiguration {

        @Bean
        public IamUserClient iamUserClient() {
            return mock(IamUserClient.class);
        }

        @Bean
        public IamApplicationClient iamApplicationClient() {
            return mock(IamApplicationClient.class);
        }
    }
}
