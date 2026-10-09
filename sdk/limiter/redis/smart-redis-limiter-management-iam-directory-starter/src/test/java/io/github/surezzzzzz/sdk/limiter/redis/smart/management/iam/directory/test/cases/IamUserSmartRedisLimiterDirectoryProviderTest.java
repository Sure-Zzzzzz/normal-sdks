package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.iam.client.model.IamUser;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.IamUserSmartRedisLimiterDirectoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IAM 用户目录适配件单测：USER 维度走 IAM、其余调用转发被装饰实现、关键字与 limit 语义
 *
 * @author surezzzzzz
 */
@Slf4j
class IamUserSmartRedisLimiterDirectoryProviderTest {

    private SmartRedisLimiterDirectoryProvider delegate;

    private IamUserClient userClient;

    private IamUserSmartRedisLimiterDirectoryProvider provider;

    private static IamUser user(String subjectId, String username, String displayName) {
        return IamUser.builder().subjectId(subjectId).username(username).displayName(displayName).build();
    }

    @BeforeEach
    void setUp() {
        delegate = mock(SmartRedisLimiterDirectoryProvider.class);
        userClient = mock(IamUserClient.class);
        provider = new IamUserSmartRedisLimiterDirectoryProvider(delegate, userClient);
    }

    @Test
    void userDimensionQueriesIamWithKeywordAndLimit() {
        when(userClient.listUsers(isNull(), isNull(), eq("ops"), eq(0), eq(20)))
                .thenReturn(IamSpringPage.<IamUser>builder()
                        .content(Collections.singletonList(user("4590442652332852", "ops-user", "运营人员")))
                        .totalElements(1).build());

        List<SmartRedisLimiterDirectoryObject> results =
                provider.listObjects("svc", "USER", "", "ops", 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDimension()).isEqualTo("USER");
        assertThat(results.get(0).getId()).isEqualTo("4590442652332852");
        assertThat(results.get(0).getName()).isEqualTo("运营人员");
        verify(userClient).listUsers(isNull(), isNull(), eq("ops"), eq(0), eq(20));
        verify(delegate, never()).listObjects(any(), any(), any(), any(), anyInt());
    }

    @Test
    void displayNameMissingFallsBackToUsername() {
        when(userClient.listUsers(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(IamSpringPage.<IamUser>builder()
                        .content(Collections.singletonList(user("1045904984437075", "plain-user", null)))
                        .totalElements(1).build());

        List<SmartRedisLimiterDirectoryObject> results =
                provider.listObjects("svc", "USER", "", "  ", 10);

        assertThat(results.get(0).getId()).isEqualTo("1045904984437075");
        assertThat(results.get(0).getName()).isEqualTo("plain-user");
    }

    @Test
    void nonUserDimensionDelegatesToDecoratedImplementation() {
        when(delegate.listObjects("svc", "CUSTOM", "tenant-a", "key", 5))
                .thenReturn(Collections.emptyList());

        List<SmartRedisLimiterDirectoryObject> results =
                provider.listObjects("svc", "CUSTOM", "tenant-a", "key", 5);

        assertThat(results).isEmpty();
        verify(delegate).listObjects("svc", "CUSTOM", "tenant-a", "key", 5);
        verify(userClient, never()).listUsers(any(), any(), any(), any(), anyInt());
    }

    @Test
    void nonPositiveLimitSkipsIamCall() {
        List<SmartRedisLimiterDirectoryObject> results =
                provider.listObjects("svc", "USER", "", null, 0);

        assertThat(results).isEmpty();
        verify(userClient, never()).listUsers(any(), any(), any(), any(), anyInt());
    }

    @Test
    void iamFailurePropagatesUnwrapped() {
        org.springframework.web.client.HttpStatusCodeException failure =
                new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY, "upstream down");
        when(userClient.listUsers(isNull(), isNull(), isNull(), eq(0), eq(10))).thenThrow(failure);

        // 透传语义：异常原样上抛，不包装、不以空列表伪装
        assertThatThrownBy(() -> provider.listObjects("svc", "USER", "", null, 10))
                .isSameAs(failure);
    }

    @Test
    void missingSubjectIdEntrySkippedNotFaked() {
        when(userClient.listUsers(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(IamSpringPage.<IamUser>builder()
                        .content(java.util.Arrays.asList(
                                user(null, "legacy-user", "旧版响应"),
                                user("7371990664432326", "limiter-nopage", "正常条目")))
                        .totalElements(2).build());

        List<SmartRedisLimiterDirectoryObject> results =
                provider.listObjects("svc", "USER", "", null, 10);

        // 缺 subjectId 的条目跳过，不以登录名冒充稳定 ID
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("7371990664432326");
    }

    @Test
    void serviceDeclarationsForwardToDecoratedImplementation() {
        when(delegate.findService("svc")).thenReturn(null);
        when(delegate.listServices()).thenReturn(Collections.emptyList());

        assertThat(provider.listServices()).isEmpty();
        assertThat(provider.findService("svc")).isNull();

        verify(delegate).listServices();
        verify(delegate).findService("svc");
        verify(userClient, never()).listUsers(any(), any(), any(), any(), anyInt());
    }
}
