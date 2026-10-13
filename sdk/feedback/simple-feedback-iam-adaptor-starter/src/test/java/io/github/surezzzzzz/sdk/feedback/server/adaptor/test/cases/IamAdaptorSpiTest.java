package io.github.surezzzzzz.sdk.feedback.server.adaptor.test.cases;

import io.github.surezzzzzz.sdk.feedback.server.adaptor.IamAccessibleApplicationProvider;
import io.github.surezzzzzz.sdk.feedback.server.adaptor.IamSubmitterProfileProvider;
import io.github.surezzzzzz.sdk.feedback.server.model.AccessibleApplication;
import io.github.surezzzzzz.sdk.feedback.server.model.SubmissionContactHint;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import io.github.surezzzzzz.sdk.iam.client.model.PageAdmittedApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IAM adaptor SPI 实现测试（邮箱三态/应用集真接口出站与降级/客户端缺席装配失败由 @Bean 直接注入保证）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {io.github.surezzzzzz.sdk.feedback.server.adaptor.test
        .SimpleFeedbackIamAdaptorTestApplication.class,
        IamAdaptorSpiTest.StubClientConfiguration.class})
class IamAdaptorSpiTest {

    @Autowired
    private IamUserClient client;
    @Autowired
    private io.github.surezzzzzz.sdk.iam.client.IamApplicationClient applicationClient;

    @Test
    @DisplayName("邮箱取数三态：有邮箱返回提示；无邮箱/出站失败返回 null 降级")
    void testContactHintThreeStates() {
        IamSubmitterProfileProvider provider = new IamSubmitterProfileProvider(client);
        log.info("输入: subject-ok / subject-noemail / subject-fail");

        SubmissionContactHint hint = provider.contactHint("subject-ok");
        assertNotNull(hint);
        assertEquals("demo@example.com", hint.getEmail());
        assertEquals("示例用户", hint.getDisplayName());
        log.info("输出: 有邮箱 hint.email={}", hint.getEmail());

        assertNull(provider.contactHint("subject-noemail"));
        assertNull(provider.contactHint("subject-fail"));
        log.info("输出: 无邮箱与出站失败均降级 null（不抛出）");
    }

    @Test
    @DisplayName("应用集取数（IAM 1.3.7 页面准入详情）：正常返回带名清单；出站失败降级 null")
    void testAccessibleApplications() {
        IamAccessibleApplicationProvider provider = new IamAccessibleApplicationProvider(client, applicationClient);
        List<AccessibleApplication> applications = provider.accessibleApplications("subject-ok");
        assertEquals(2, applications.size());
        assertEquals("alpha-app", applications.get(0).getApplicationCode());
        assertEquals("应用甲", applications.get(0).getDisplayName());
        assertEquals("应用乙", applications.get(1).getDisplayName());
        log.info("输出: 应用详情条数={} 首项名称={}", applications.size(),
                applications.get(0).getDisplayName());

        assertNull(provider.accessibleApplications("subject-fail"));
        log.info("输出: 出站失败降级 null（不拒提交）");
    }

    @Test
    @DisplayName("目录取数（注册表来源核对）：翻页拉全含官方名；出站失败降级 null（注册表不锁）")
    void testCatalogApplications() {
        IamAccessibleApplicationProvider provider = new IamAccessibleApplicationProvider(client, applicationClient);
        List<AccessibleApplication> catalog = provider.catalogApplications();
        assertEquals(2, catalog.size());
        assertEquals("kms", catalog.get(0).getApplicationCode());
        assertEquals("KMS 密钥管理", catalog.get(0).getDisplayName());
        assertNull(catalog.get(0).getDescription(), "目录投影无描述字段");
        log.info("输出: 目录条数={} 首项={}", catalog.size(), catalog.get(0).getDisplayName());

        IamUserClient failUserClient = mock(IamUserClient.class);
        io.github.surezzzzzz.sdk.iam.client.IamApplicationClient failApplicationClient =
                mock(io.github.surezzzzzz.sdk.iam.client.IamApplicationClient.class);
        when(failApplicationClient.listApplications(0, IamAccessibleApplicationProvider.CATALOG_PAGE_SIZE))
                .thenThrow(new IllegalStateException("stub outbound failure"));
        IamAccessibleApplicationProvider failProvider =
                new IamAccessibleApplicationProvider(failUserClient, failApplicationClient);
        assertNull(failProvider.catalogApplications());
        log.info("输出: 目录出站失败降级 null（来源未知，注册表保持全可编辑）");
    }

    /**
     * stub IAM 客户端：getUser/listPageAdmittedApplicationDetails/listApplications 按参数返回固定值或抛异常。
     */
    @TestConfiguration
    static class StubClientConfiguration {

        @Bean
        public IamUserClient iamUserClient() {
            IamUserClient client = mock(IamUserClient.class);
            IamUser user = IamUser.builder()
                    .id(1L)
                    .username("demo-user")
                    .displayName("示例用户")
                    .email("demo@example.com")
                    .build();
            when(client.getUser("subject-ok")).thenReturn(user);
            when(client.getUser("subject-noemail")).thenReturn(IamUser.builder()
                    .id(2L).username("no-email").displayName("无邮箱").build());
            when(client.getUser("subject-fail")).thenThrow(new IllegalStateException("stub outbound failure"));
            when(client.listPageAdmittedApplicationDetails("subject-ok"))
                    .thenReturn(Arrays.asList(
                            PageAdmittedApplication.builder()
                                    .code("alpha-app").displayName("应用甲").description("甲的说明").build(),
                            PageAdmittedApplication.builder()
                                    .code("beta-app").displayName("应用乙").description(null).build()));
            when(client.listPageAdmittedApplicationDetails("subject-fail"))
                    .thenThrow(new IllegalStateException("stub outbound failure"));
            return client;
        }

        @Bean
        public io.github.surezzzzzz.sdk.iam.client.IamApplicationClient iamApplicationClient() {
            io.github.surezzzzzz.sdk.iam.client.IamApplicationClient client =
                    mock(io.github.surezzzzzz.sdk.iam.client.IamApplicationClient.class);
            when(client.listApplications(0, 100)).thenReturn(
                    io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage.<io.github.surezzzzzz.sdk.iam.client.model.IamApplication>builder()
                            .content(Arrays.asList(
                                    io.github.surezzzzzz.sdk.iam.client.model.IamApplication.builder()
                                            .applicationCode("kms").applicationName("KMS 密钥管理").status(1).build(),
                                    io.github.surezzzzzz.sdk.iam.client.model.IamApplication.builder()
                                            .applicationCode("iam").applicationName("统一身份").status(1).build()))
                            .totalElements(2L)
                            .totalPages(1)
                            .build());
            return client;
        }
    }
}
