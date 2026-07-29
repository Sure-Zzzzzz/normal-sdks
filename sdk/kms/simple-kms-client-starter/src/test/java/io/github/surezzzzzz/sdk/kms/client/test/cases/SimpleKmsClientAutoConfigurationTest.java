package io.github.surezzzzzz.sdk.kms.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.client.KmsClientAuthenticationInterceptor;
import io.github.surezzzzzz.sdk.kms.client.configuration.SimpleKmsClientAutoConfiguration;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsClientConfigurationException;
import io.github.surezzzzzz.sdk.kms.client.model.*;
import io.github.surezzzzzz.sdk.kms.client.port.KeyEncryptionPort;
import io.github.surezzzzzz.sdk.kms.client.port.TenantPublicKeyPort;
import io.github.surezzzzzz.sdk.kms.client.port.TenantSignerPort;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpErrorMapper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple KMS Client 自动配置与扩展点替换测试。
 *
 * <p>ApplicationContextRunner 只装载本模块自动配置，用于验证默认 传输 不污染调用方自定义 Client。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleKmsClientAutoConfigurationTest {

    /**
     * 不通过 Spring Boot 元数据 发现，隔离验证自动配置类的条件装配行为。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimpleKmsClientAutoConfiguration.class);

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Test
    void shouldStayDisabledByDefault() {
        contextRunner.run(context -> {
            log.info("默认配置 Bean 数量: {}", context.getBeanDefinitionCount());
            assertFalse(context.containsBean("kmsClient"), "默认不得创建 HTTP Client");
            assertFalse(context.containsBean("simpleKmsClientRestTemplate"), "默认不得创建专属 RestTemplate");
            assertNotNull(context.getBean(KmsJsonCodec.class), "独立 JSON 编解码器必须可用");
            assertNotNull(context.getBean(KmsHttpErrorMapper.class), "默认错误映射器必须可用");
        });
    }

    @Test
    void shouldCreateDedicatedTransportWhenEnabledWithValidOrigin() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enabled=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal/")
                .run(context -> {
                    log.info("启用默认 Client 后 Bean 数量: {}", context.getBeanDefinitionCount());
                    assertNull(context.getStartupFailure(), "合法 origin 必须能完成自动配置");
                    assertNotNull(context.getBean(KmsClient.class), "启用后必须创建默认 KmsClient");
                    assertNotNull(context.getBean("simpleKmsClientRestTemplate", RestTemplate.class), "必须创建专属 RestTemplate");
                    assertNotNull(context.getBean(KmsHttpExecutor.class), "必须创建限长执行器");
                    assertNotNull(context.getBean(TenantSignerPort.class), "默认签名端口必须可注入");
                    assertNotNull(context.getBean(TenantPublicKeyPort.class), "默认公钥端口必须可注入");
                    assertNotNull(context.getBean(KeyEncryptionPort.class), "默认加解密端口必须可注入");
                });
    }

    @Test
    void shouldRejectInvalidOriginWhenEnabled() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enabled=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal/untrusted-path")
                .run(context -> {
                    log.info("非法 origin 启动异常类型: {}", context.getStartupFailure().getClass().getName());
                    assertNotNull(context.getStartupFailure(), "非法 base URL 必须启动失败");
                    assertTrue(hasCause(context.getStartupFailure(), KmsClientConfigurationException.class),
                            "非法 base URL 必须保留 Client 配置异常类型");
                });
    }

    @Test
    void shouldExposeMinimalPortsForCustomClientWithoutHttpConfiguration() {
        contextRunner.withBean(KmsClient.class, StubKmsClient::new).run(context -> {
            log.info("自定义 Client 时 HTTP transport 是否存在: {}", context.containsBean("simpleKmsClientRestTemplate"));
            assertTrue(context.containsBean("tenantSignerPort"), "自定义 Client 必须获得默认签名端口");
            assertTrue(context.containsBean("tenantPublicKeyPort"), "自定义 Client 必须获得默认公钥端口");
            assertTrue(context.containsBean("keyEncryptionPort"), "自定义 Client 必须获得默认加解密端口");
            assertNotNull(context.getBean(TenantSignerPort.class), "签名端口必须可注入");
            assertNotNull(context.getBean(TenantPublicKeyPort.class), "公钥端口必须可注入");
            assertNotNull(context.getBean(KeyEncryptionPort.class), "加解密端口必须可注入");
            assertFalse(context.containsBean("simpleKmsClientRestTemplate"), "自定义 Client 时不得创建默认 transport");
            assertFalse(context.containsBean("simpleKmsClientHttpClient"), "自定义 Client 时不得创建默认 HTTP 连接池");
        });
    }

    @Test
    void shouldOnlyReplaceExplicitMinimalPort() {
        TenantSignerPort customSignerPort = (keyRef, version, signingInput) -> null;
        contextRunner.withBean(KmsClient.class, StubKmsClient::new)
                .withBean(TenantSignerPort.class, () -> customSignerPort)
                .run(context -> {
                    log.info("自定义最小端口后签名端口类型: {}", context.getBean(TenantSignerPort.class).getClass().getName());
                    assertEquals(customSignerPort, context.getBean(TenantSignerPort.class), "自定义签名端口必须保持优先级");
                    assertNotNull(context.getBean(TenantPublicKeyPort.class), "其他默认端口不得被自定义签名端口影响");
                    assertNotNull(context.getBean(KeyEncryptionPort.class), "其他默认端口不得被自定义签名端口影响");
                });
    }

    @Test
    void shouldUseOnlyOneDedicatedAuthenticationInterceptor() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enabled=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal")
                .withBean(KmsClientAuthenticationInterceptor.class, AuthenticationInterceptor::new)
                .run(context -> {
                    RestTemplate restTemplate = context.getBean("simpleKmsClientRestTemplate", RestTemplate.class);
                    log.info("专属认证拦截器数量: {}", restTemplate.getInterceptors().size());
                    assertEquals(Integer.valueOf(1), Integer.valueOf(restTemplate.getInterceptors().size()),
                            "专属 RestTemplate 只能装配一个 KMS 认证拦截器");
                    assertTrue(restTemplate.getInterceptors().get(0) instanceof KmsClientAuthenticationInterceptor,
                            "装配的拦截器必须是 KMS 专用认证扩展点");
                });
    }

    @Test
    void shouldRejectMultipleDedicatedAuthenticationInterceptors() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enabled=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal")
                .withBean("firstAuthenticationInterceptor", KmsClientAuthenticationInterceptor.class,
                        AuthenticationInterceptor::new)
                .withBean("secondAuthenticationInterceptor", KmsClientAuthenticationInterceptor.class,
                        AuthenticationInterceptor::new)
                .run(context -> {
                    log.info("多个认证拦截器启动异常类型: {}", context.getStartupFailure().getClass().getName());
                    assertNotNull(context.getStartupFailure(), "多个 KMS 认证拦截器必须启动失败");
                    assertTrue(hasCause(context.getStartupFailure(), KmsClientConfigurationException.class),
                            "多个认证拦截器必须映射为 Client 配置错误");
                });
    }

    @Test
    void shouldRespectCustomCodecErrorMapperAndExecutor() {
        KmsJsonCodec customCodec = new KmsJsonCodec();
        KmsHttpErrorMapper customErrorMapper = new KmsHttpErrorMapper();
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enabled=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal")
                .withBean(KmsJsonCodec.class, () -> customCodec)
                .withBean(KmsHttpErrorMapper.class, () -> customErrorMapper)
                .withBean(KmsHttpExecutor.class, () -> new KmsHttpExecutor(new RestTemplate(), customCodec,
                        customErrorMapper, 1024, 1024))
                .run(context -> {
                    log.info("自定义扩展点是否保持原实例: codec={}, mapper={}, executor={}",
                            context.getBean(KmsJsonCodec.class) == customCodec,
                            context.getBean(KmsHttpErrorMapper.class) == customErrorMapper,
                            context.containsBean("kmsHttpExecutor"));
                    assertEquals(customCodec, context.getBean(KmsJsonCodec.class), "自定义 codec 必须替代默认实现");
                    assertEquals(customErrorMapper, context.getBean(KmsHttpErrorMapper.class), "自定义错误映射器必须替代默认实现");
                    assertEquals(Integer.valueOf(1), Integer.valueOf(context.getBeansOfType(KmsHttpExecutor.class).size()),
                            "自定义执行器必须阻止默认执行器创建");
                    assertFalse(context.containsBean("simpleKmsClientRestTemplate"),
                            "自定义执行器不得创建未使用的专属 RestTemplate");
                    assertFalse(context.containsBean("simpleKmsClientHttpClient"),
                            "自定义执行器不得创建未使用的专属 HTTP 连接池");
                    assertNotNull(context.getBean(KmsClient.class), "自定义执行器下仍必须创建默认 KmsClient");
                });
    }

    private static class AuthenticationInterceptor implements KmsClientAuthenticationInterceptor {
        @Override
        public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            return execution.execute(request, body);
        }
    }

    /**
     * 只用于证明最小端口可绑定调用方自定义 Client，任何 HTTP 操作都不应进入该桩实现。
     */
    private static class StubKmsClient implements KmsClient {
        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException();
        }

        @Override
        public KmsKey createKey(String idempotencyKey, String keyAlias, String purpose, String algorithm) {
            throw unsupported();
        }

        @Override
        public KmsKey getKey(String keyRef) {
            throw unsupported();
        }

        @Override
        public KmsKeyPage listKeys(Integer page, Integer size, String alias, String purpose, String algorithm,
                                   String state) {
            throw unsupported();
        }

        @Override
        public KmsKey changeKeyState(String idempotencyKey, String keyRef, String state, Long expectedRowVersion) {
            throw unsupported();
        }

        @Override
        public KmsKey rotateKey(String idempotencyKey, String keyRef, Long expectedRowVersion) {
            throw unsupported();
        }

        @Override
        public KmsKey scheduleDestruction(String idempotencyKey, String keyRef, Instant destroyAfter,
                                          Long expectedRowVersion) {
            throw unsupported();
        }

        @Override
        public void cancelDestruction(String idempotencyKey, String keyRef, Long expectedRowVersion) {
            throw unsupported();
        }

        @Override
        public KmsPolicy createPolicy(String idempotencyKey, String keyRef, String principalId, Integer keyVersion,
                                      String operation, Instant expiresAt) {
            throw unsupported();
        }

        @Override
        public List<KmsPolicy> listPolicies(String keyRef) {
            throw unsupported();
        }

        @Override
        public void revokePolicy(String idempotencyKey, String keyRef, String policyId, Long expectedRowVersion) {
            throw unsupported();
        }

        @Override
        public KmsSignature sign(String keyRef, Integer version, byte[] signingInput) {
            throw unsupported();
        }

        @Override
        public boolean verify(String keyRef, Integer version, byte[] signingInput, byte[] signature) {
            throw unsupported();
        }

        @Override
        public byte[] encrypt(String keyRef, byte[] plaintext, byte[] aad) {
            throw unsupported();
        }

        @Override
        public byte[] decrypt(byte[] envelope, byte[] aad) {
            throw unsupported();
        }

        @Override
        public KmsPublicKey readPublicKey(String keyRef, Integer version) {
            throw unsupported();
        }

        @Override
        public List<KmsPublicKey> listPublicKeys(String keyRef) {
            throw unsupported();
        }
    }
}
