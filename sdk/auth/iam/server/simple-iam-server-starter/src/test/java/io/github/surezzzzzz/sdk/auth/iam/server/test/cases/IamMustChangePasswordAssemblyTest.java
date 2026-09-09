package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 须改密防线装配开关测试。
 *
 * <p>password.must-change-enforcement 是侵入型防线（接管全部 API）的逃生口：
 * 测试基座批量建号场景关闭；本类验证显式 false 时拦截器 bean 不装配
 * （四条会话链上 addMustChangePasswordFilter 均跳过），显式 true 时装配。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.auth.iam.server.password.must-change-enforcement=false"
})
class IamMustChangePasswordAssemblyTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void testEnforcementDisabledDoesNotAssembleFilterBean() {
        assertFalse(applicationContext.containsBean("iamMustChangePasswordFilter"),
                "must-change-enforcement=false 时防线拦截器 bean 不应装配（四链均不挂）");
        log.info("✓ must-change-enforcement=false：IamMustChangePasswordFilter 不装配");
    }

    @SpringBootTest(
            classes = SimpleIamServerTestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {
                    "io.github.surezzzzzz.sdk.auth.iam.server.password.must-change-enforcement=true"
            }
    )
    static class EnabledExplicitlyNested {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void testEnforcementEnabledAssemblesFilterBean() {
            assertTrue(applicationContext.containsBean("iamMustChangePasswordFilter"),
                    "must-change-enforcement=true（生产默认口径）时防线拦截器应装配");
            log.info("✓ must-change-enforcement=true：IamMustChangePasswordFilter 装配");
        }
    }
}
