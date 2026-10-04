package io.github.surezzzzzz.sdk.kms.feign.client.test.demo;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.feign.client.KmsFeignClient;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsKeyPageResponse;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsKeyResponse;
import io.github.surezzzzzz.sdk.kms.feign.client.model.KmsSignResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Feign 形态端到端联调宿主：完整 aksk 底座真链 + AKP → KMS 六步。
 *
 * <p>链路与凭据全部来自 src/test/resources 配置（application.yml 结构 + gitignored
 * application-local.yml 真实凭据，模板见 application-local.yml.example）：底座令牌链经
 * {@code @AkskClientFeignClient} 元注解自动向 Feign 请求注入 Authorization 头，
 * 六步断言失败即抛。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public final class KmsAkpFeignIntegrationRunner {
    private KmsAkpFeignIntegrationRunner() {
    }

    /**
     * 联调入口。
     *
     * @param args 未使用
     * @throws Exception HTTP 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(KmsAkpFeignIntegrationRunner.RunnerConfiguration.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Collections.singletonMap("spring.main.banner-mode", "off"));
        ConfigurableApplicationContext context = app.run();
        KmsFeignClient client = context.getBean(KmsFeignClient.class);

        String clientId = context.getEnvironment()
                .getProperty("io.github.surezzzzzz.sdk.auth.aksk.client.client-id");
        String owner = "aksk:" + clientId;
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        log.info("[FEIGN] owner={} unique={}", owner, unique);

        KmsKeyResponse key = client.createKey("feign-" + unique, body(
                SimpleKmsClientConstant.FIELD_KEY_ALIAS, "feign-" + unique,
                SimpleKmsClientConstant.FIELD_PURPOSE, "SIGN",
                SimpleKmsClientConstant.FIELD_ALGORITHM, "ES256"));
        require("ACTIVE".equals(key.state), "[1] create 后状态必须为 ACTIVE");

        KmsKeyPageResponse page = client.listKeys(1, 100, "feign-" + unique, "SIGN", "ES256", null);
        boolean listed = page.items.stream().anyMatch(item -> key.keyRef.equals(item.keyRef));
        require(listed, "[2] 列表必须且只能看到自己的密钥");

        client.createPolicy("feign-sign-" + unique, key.keyRef, body(
                SimpleKmsClientConstant.FIELD_PRINCIPAL_ID, owner,
                SimpleKmsClientConstant.FIELD_OPERATION, "SIGN"));
        client.createPolicy("feign-verify-" + unique, key.keyRef, body(
                SimpleKmsClientConstant.FIELD_PRINCIPAL_ID, owner,
                SimpleKmsClientConstant.FIELD_OPERATION, "VERIFY"));

        String input = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("feign-" + unique).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        KmsSignResponse sign = client.sign(body(
                SimpleKmsClientConstant.FIELD_KEY_REF, key.keyRef,
                SimpleKmsClientConstant.FIELD_INPUT, input));
        require(sign.signature != null && !sign.signature.isEmpty(), "[4] 签名必须返回非空 Base64url");

        Boolean verified = client.verify(body(
                SimpleKmsClientConstant.FIELD_KEY_REF, key.keyRef,
                SimpleKmsClientConstant.FIELD_INPUT, input,
                SimpleKmsClientConstant.FIELD_SIGNATURE, sign.signature)).valid;
        require(Boolean.TRUE.equals(verified), "[5] 验签必须通过");

        String dueAt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.now().plusSeconds(7200).truncatedTo(ChronoUnit.MILLIS));
        client.scheduleDestruction("feign-dest-" + unique, key.keyRef, body(
                SimpleKmsClientConstant.FIELD_DUE_AT, dueAt,
                SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, key.rowVersion));
        log.info("[FEIGN] ALL 6 STEPS PASSED");
        context.close();
    }

    private static Map<String, Object> body(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 宿主配置：启用 Feign 扫描本模块契约接口，令牌链与地址全部来自配置文件。
     */
    @Configuration
    @EnableAutoConfiguration
    @EnableFeignClients(basePackageClasses = KmsFeignClient.class)
    static class RunnerConfiguration {
    }
}
