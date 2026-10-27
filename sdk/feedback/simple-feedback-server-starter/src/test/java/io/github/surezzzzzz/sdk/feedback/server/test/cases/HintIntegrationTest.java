package io.github.surezzzzzz.sdk.feedback.server.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.fixture.ServiceTestResourceAdapterConfiguration;
import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.FeedbackApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;
import io.github.surezzzzzz.sdk.feedback.server.service.ApplicationRegistryService;
import io.github.surezzzzzz.sdk.feedback.server.service.HintService;
import io.github.surezzzzzz.sdk.feedback.server.spi.AccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.spi.SubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.test.SimpleFeedbackServerTestApplication;
import io.github.surezzzzzz.sdk.feedback.server.test.support.FeedbackTestSchemaHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * hint 交集三态集成测试（交集命中/空集回退/出站失败降级/散形态降级；真 MySQL 注册表）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleFeedbackServerTestApplication.class,
        ServiceTestResourceAdapterConfiguration.class,
        HintIntegrationTest.IntersectStubConfiguration.class})
class HintIntegrationTest {

    @Autowired
    private HintService hintService;
    @Autowired
    private ApplicationRegistryService registryService;

    /**
     * 受控 schema 重置（全类一次）。
     *
     * @param dataSource 测试数据源
     */
    @BeforeAll
    static void resetSchema(@Autowired DataSource dataSource) {
        FeedbackTestSchemaHelper.reset(dataSource);
    }

    private static List<String> codesOf(HintService.SubmissionHint hint) {
        List<String> codes = new java.util.ArrayList<String>();
        for (AccessibleApplication application : hint.getApplications()) {
            codes.add(application.getApplicationCode());
        }
        return codes;
    }

    @Test
    @DisplayName("hint 三态：交集命中=交集且名称取 IAM 同源；空集/出站失败=回退注册表全量（注册表名）；邮箱预填透传")
    void testHintThreeStates() {
        registryService.create(FeedbackApplication.builder()
                .applicationCode("alpha-app").displayName("甲").enabled(true).createdBy("admin").build(), "admin");
        registryService.create(FeedbackApplication.builder()
                .applicationCode("beta-app").displayName("乙").enabled(true).createdBy("admin").build(), "admin");
        registryService.create(FeedbackApplication.builder()
                .applicationCode("gamma-app").displayName("丙").enabled(false).createdBy("admin").build(), "admin");
        log.info("输入: 注册表 alpha/beta 启用（展示名 甲/乙），gamma 停用；stub 聚形态名称=门户同源-甲/乙");

        // 第一态：交集命中（gamma 停用不入集）——展示名取 IAM 同源名而非注册表名
        HintService.SubmissionHint intersected = hintService.hint("subject-1");
        assertEquals(Arrays.asList("alpha-app", "beta-app"), codesOf(intersected));
        assertFalse(codesOf(intersected).contains("gamma-app"));
        assertEquals("门户同源-甲", intersected.getApplications().get(0).getDisplayName());
        assertEquals("preset@example.com", intersected.getContactEmail());
        log.info("输出: 交集命中应用数={} 首项展示名={}（IAM 同源）", intersected.getApplications().size(),
                intersected.getApplications().get(0).getDisplayName());

        // 第二态：空集回退全量（启用项）——名称回退注册表展示名
        HintService.SubmissionHint emptyFallback = hintService.hint("subject-1");
        assertEquals(Arrays.asList("alpha-app", "beta-app"), codesOf(emptyFallback));
        assertEquals("甲", emptyFallback.getApplications().get(0).getDisplayName());
        log.info("输出: 空集回退后应用数={} 名称=注册表展示名", emptyFallback.getApplications().size());

        // 第三态：出站失败降级（stub 第三次抛异常）→ 回退全量，不拒提交
        HintService.SubmissionHint failureFallback = hintService.hint("subject-1");
        assertEquals(Arrays.asList("alpha-app", "beta-app"), codesOf(failureFallback));
        assertTrue(failureFallback.getContactEmail() != null);
        log.info("输出: 出站失败降级后应用数={}（不拒提交）", failureFallback.getApplications().size());
    }

    /**
     * 可控 stub：三次调用分别返回交集命中集/空集/抛异常（顺序驱动断言）。
     */
    @TestConfiguration
    static class IntersectStubConfiguration {

        @Bean
        @Primary
        public AccessibleApplicationProvider stubAccessibleProvider() {
            return new AccessibleApplicationProvider() {
                private int call;

                @Override
                public List<AccessibleApplication> accessibleApplications(String subjectId) {
                    call++;
                    if (call == 1) {
                        return Arrays.asList(
                                AccessibleApplication.builder()
                                        .applicationCode("alpha-app").displayName("门户同源-甲").build(),
                                AccessibleApplication.builder()
                                        .applicationCode("beta-app").displayName("门户同源-乙").build());
                    }
                    if (call == 2) {
                        return Arrays.asList();
                    }
                    throw new RuntimeException("stub outbound failure");
                }
            };
        }

        @Bean
        @Primary
        public SubmitterProfileProvider stubProfileProvider() {
            return subjectId -> new SubmissionContactHint("preset@example.com", "预填用户");
        }
    }
}
