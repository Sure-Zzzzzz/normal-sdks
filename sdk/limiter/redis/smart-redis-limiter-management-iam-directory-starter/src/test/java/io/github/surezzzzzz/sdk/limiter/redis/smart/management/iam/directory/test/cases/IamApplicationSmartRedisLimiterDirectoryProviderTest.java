package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamApplicationClient;
import io.github.surezzzzzz.sdk.iam.client.model.IamApplication;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterServiceControlMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.IamApplicationRegistrySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.IamApplicationSmartRedisLimiterDirectoryProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IAM 应用目录适配件单测：清单权威化规则矩阵（启用+声明/启用未声明/停用/未登记）、
 * 连通类故障本地完整兜底（与散形态等价）、授权类与版本错配响亮上抛、60 秒快照单飞、
 * 对象检索转发
 *
 * @author surezzzzzz
 */
@Slf4j
class IamApplicationSmartRedisLimiterDirectoryProviderTest {

    private SmartRedisLimiterDirectoryProvider delegate;

    private IamApplicationClient applicationClient;

    private IamApplicationSmartRedisLimiterDirectoryProvider provider;

    private static IamApplication app(String code, String name, Integer status) {
        return IamApplication.builder().applicationCode(code).applicationName(name).status(status).build();
    }

    private static IamSpringPage<IamApplication> page(int total, IamApplication... applications) {
        return IamSpringPage.<IamApplication>builder()
                .content(Arrays.asList(applications)).totalElements(total).build();
    }

    /**
     * 本地完整声明（散形态答案）：iam 带语义 + 编造服务（注册表不存在）
     */
    private static SmartRedisLimiterServiceDeclaration declared(String serviceCode, String displayName) {
        SmartRedisLimiterServiceDeclaration declaration = new SmartRedisLimiterServiceDeclaration();
        declaration.setServiceCode(serviceCode);
        declaration.setControlMode(SmartRedisLimiterServiceControlMode.TYPED_V2.name());
        declaration.setDisplayName(displayName);
        declaration.setResources(new LinkedHashMap<>(Collections.singletonMap(
                "iam-web-csrf", Collections.singletonList("IP"))));
        declaration.setNamespaces(new LinkedHashMap<>(Collections.singletonMap("IP", "entry")));
        return declaration;
    }

    @BeforeEach
    void setUp() {
        delegate = mock(SmartRedisLimiterDirectoryProvider.class);
        applicationClient = mock(IamApplicationClient.class);
        when(delegate.listServices()).thenReturn(Arrays.asList(
                declared("iam", "IAM 管理台"), declared("ghost", "编造服务")));
        when(delegate.findService("iam")).thenReturn(declared("iam", "IAM 管理台"));
        when(delegate.findService("ghost")).thenReturn(declared("ghost", "编造服务"));
        // TTL=0：每个用例自主控制快照时序
        provider = new IamApplicationSmartRedisLimiterDirectoryProvider(
                delegate, new IamApplicationRegistrySnapshot(applicationClient, 0L));
    }

    @Test
    void enabledDeclaredServiceAppearsWithRegistryNameAndAnnotatedSemantics() {
        when(applicationClient.listApplications(0, 100))
                .thenReturn(page(2, app("iam", "IAM 管理台", 1), app("kms", "KMS 密钥管理", 1)));

        List<SmartRedisLimiterServiceDeclaration> services = provider.listServices();

        assertThat(services).extracting(SmartRedisLimiterServiceDeclaration::getServiceCode)
                .containsExactly("iam", "kms");
        SmartRedisLimiterServiceDeclaration iam = services.get(0);
        assertThat(iam.getDisplayName()).isEqualTo("IAM 管理台");
        assertThat(iam.getResources()).containsKey("iam-web-csrf");
        assertThat(iam.getNamespaces()).containsEntry("IP", "entry");
    }

    @Test
    void enabledUndeclaredServiceAppearsWithEmptySemantics() {
        when(applicationClient.listApplications(0, 100))
                .thenReturn(page(1, app("crm", "CRM 管理台", 1)));

        List<SmartRedisLimiterServiceDeclaration> services = provider.listServices();

        assertThat(services).hasSize(1);
        assertThat(services.get(0).getServiceCode()).isEqualTo("crm");
        assertThat(services.get(0).getDisplayName()).isEqualTo("CRM 管理台");
        assertThat(services.get(0).getResources()).isEmpty();
        assertThat(provider.findService("crm").getDisplayName()).isEqualTo("CRM 管理台");
    }

    @Test
    void disabledAndUnregisteredDeclarationsHidden() {
        when(applicationClient.listApplications(0, 100))
                .thenReturn(page(2, app("iam", "IAM 管理台", 1), app("kms", "KMS 密钥管理", 0)));

        List<SmartRedisLimiterServiceDeclaration> services = provider.listServices();

        // kms 停用不出现；ghost 声明存在但注册表无此 code 不出现
        assertThat(services).extracting(SmartRedisLimiterServiceDeclaration::getServiceCode)
                .containsExactly("iam");
        assertThat(provider.findService("kms")).isNull();
        assertThat(provider.findService("ghost")).isNull();
    }

    @Test
    void connectivityFailureFallsBackToScatteredEquivalent() {
        when(applicationClient.listApplications(0, 100))
                .thenThrow(new ResourceAccessException("connect timeout"));
        List<SmartRedisLimiterServiceDeclaration> scattered = delegate.listServices();

        List<SmartRedisLimiterDirectoryObject> delegateObjects =
                Collections.singletonList(new SmartRedisLimiterDirectoryObject("CUSTOMER", "", "cust-1", "客户"));
        when(delegate.listObjects("iam", "CUSTOMER", "", "客", 10)).thenReturn(delegateObjects);

        // 回退输出与散形态完全等价：清单与对象均原样来自本地声明
        assertThat(provider.listServices()).isEqualTo(scattered);
        assertThat(provider.findService("iam")).isEqualTo(delegate.findService("iam"));
        assertThat(provider.listObjects("iam", "CUSTOMER", "", "客", 10)).isSameAs(delegateObjects);
    }

    @Test
    void recoveryReturnsToRegistryAuthority() {
        when(applicationClient.listApplications(0, 100))
                .thenThrow(new ResourceAccessException("down"))
                .thenReturn(page(1, app("iam", "IAM 管理台", 1)));

        assertThat(provider.listServices()).isEqualTo(delegate.listServices());
        assertThat(provider.listServices()).extracting(SmartRedisLimiterServiceDeclaration::getServiceCode)
                .containsExactly("iam");
    }

    @Test
    void serverErrorFallsBackButAuthorizationAndVersionMismatchThrow() {
        // 顺序链：第 1 次连通类 5xx 回退；第 2 次 403（授权缺口）与第 3 次 404（服务端低于
        // 1.3.7 无端点）响亮上抛——静默兜底会把配置错误伪装成"注册表里没有"
        when(applicationClient.listApplications(0, 100)).thenThrow(
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY, "upstream"),
                HttpClientErrorException.create(HttpStatus.FORBIDDEN, "denied", null, null, null),
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "endpoint missing", null, null, null));

        assertThat(provider.listServices()).isEqualTo(delegate.listServices());
        assertThatThrownBy(() -> provider.listServices())
                .isInstanceOf(HttpClientErrorException.class);
        assertThatThrownBy(() -> provider.listServices())
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void snapshotWithinTtlLoadsOnce() {
        IamApplicationSmartRedisLimiterDirectoryProvider cached = new IamApplicationSmartRedisLimiterDirectoryProvider(
                delegate, new IamApplicationRegistrySnapshot(applicationClient, 60_000L));
        when(applicationClient.listApplications(0, 100))
                .thenReturn(page(1, app("iam", "IAM 管理台", 1)));

        cached.listServices();
        cached.listServices();
        cached.findService("iam");

        verify(applicationClient, times(1)).listApplications(0, 100);
    }

    @Test
    void paginationLoadsAllPagesUntilTotalReached() {
        when(applicationClient.listApplications(eq(0), anyInt()))
                .thenReturn(IamSpringPage.<IamApplication>builder()
                        .content(Collections.singletonList(app("iam", "IAM 管理台", 1))).totalElements(2).build());
        when(applicationClient.listApplications(eq(1), anyInt()))
                .thenReturn(IamSpringPage.<IamApplication>builder()
                        .content(Collections.singletonList(app("aksk", "AKSK访问凭证管理", 1))).totalElements(2).build());

        assertThat(provider.listServices())
                .extracting(SmartRedisLimiterServiceDeclaration::getServiceCode)
                .containsExactly("iam", "aksk");
        verify(applicationClient).listApplications(eq(1), anyInt());
    }

    @Test
    void decoratedDeclarationIsCopyNotSharedReference() {
        when(applicationClient.listApplications(0, 100))
                .thenReturn(page(1, app("iam", "注册表新名", 1)));
        SmartRedisLimiterServiceDeclaration before = delegate.findService("iam");

        provider.listServices();

        // 装饰返回拷贝：本地声明的 displayName 不被改写（副本不是第二权威，但也不反向污染）
        assertThat(before.getDisplayName()).isEqualTo("IAM 管理台");
        Map<String, List<String>> beforeResources = before.getResources();
        assertThat(beforeResources).containsKey("iam-web-csrf");
    }

    @Test
    void objectSearchForwardsToDelegate() {
        when(delegate.listObjects(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Collections.emptyList());

        assertThat(provider.listObjects("iam", "USER", "", "ops", 10)).isEmpty();

        verify(delegate).listObjects("iam", "USER", "", "ops", 10);
        verify(applicationClient, never()).listApplications(anyInt(), anyInt());
    }
}
