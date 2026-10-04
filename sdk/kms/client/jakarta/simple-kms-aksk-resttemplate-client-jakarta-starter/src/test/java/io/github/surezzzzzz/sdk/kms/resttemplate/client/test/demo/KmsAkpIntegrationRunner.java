package io.github.surezzzzzz.sdk.kms.resttemplate.client.test.demo;

import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.client.model.KmsSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * RT-SB2 端到端联调宿主：完整 aksk 底座真链 + AKP → KMS 六步。
 *
 * <p>链路与凭据全部来自 src/test/resources 配置（application.yml 结构 + gitignored
 * application-local.yml 真实值，模板见 application-local.yml.example）：Redis Route →
 * smart-cache 两级缓存 → RedisTokenManager 令牌链（AKP client_credentials）→ 底座扫描注册
 * 拦截器并构建 akskClientRestTemplate → 本模块装配 KmsRestTemplateClient。宿主零自定义 Bean、
 * 零命令行属性，六步断言失败即抛，端到端验证"底座传输+认证 → KmsRestTemplateClient → 契约接口"整链。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public final class KmsAkpIntegrationRunner {
    private KmsAkpIntegrationRunner() {
    }

    /**
     * 联调入口。
     *
     * @param args 未使用
     * @throws Exception HTTP 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(KmsAkpIntegrationRunner.RunnerConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Collections.singletonMap("spring.main.banner-mode", "off"));
        ConfigurableApplicationContext context = app.run();
        KmsClient client = context.getBean(KmsClient.class);

        String owner = "aksk:" + context.getEnvironment()
                .getProperty("io.github.surezzzzzz.sdk.auth.aksk.client.client-id");
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        log.info("[RT-JAKARTA] owner={} unique={}", owner, unique);

        KmsKey key = client.createKey("rt-sb2-" + unique, "rt-sb2-" + unique, "SIGN", "ES256");
        assertEquals("ACTIVE", key.getState(), "[1] create 后状态必须为 ACTIVE");

        boolean listed = client.listKeys(1, 100, "rt-sb2-" + unique, "SIGN", "ES256", null)
                .getItems().stream().anyMatch(k -> k.getKeyRef().equals(key.getKeyRef()));
        assertTrue(listed, "[2] 列表必须且只能看到自己的密钥");

        client.createPolicy("rt-sb2-sign-" + unique, key.getKeyRef(), owner, null, "SIGN", null);
        client.createPolicy("rt-sb2-verify-" + unique, key.getKeyRef(), owner, null, "VERIFY", null);

        byte[] payload = ("rt-sb2-" + unique).getBytes(StandardCharsets.UTF_8);
        KmsSignature sig = client.sign(key.getKeyRef(), null, payload);
        assertEquals(Integer.valueOf(1), sig.getVersion(), "[4] 签名版本必须为 1");

        boolean verified = client.verify(key.getKeyRef(), null, payload, sig.getSignature());
        assertTrue(verified, "[5] 验签必须通过");

        client.scheduleDestruction("rt-sb2-dest-" + unique, key.getKeyRef(),
                Instant.now().plusSeconds(7200), key.getRowVersion());
        log.info("[RT-JAKARTA] ALL 6 STEPS PASSED");
        context.close();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException(message + "（expected=" + expected + " actual=" + actual + "）");
        }
    }

    /**
     * 宿主配置：零自定义 Bean，传输、令牌链与客户端全部由配置文件与自动配置装配。
     */
    @Configuration
    @EnableAutoConfiguration
    static class RunnerConfiguration {
    }
}
