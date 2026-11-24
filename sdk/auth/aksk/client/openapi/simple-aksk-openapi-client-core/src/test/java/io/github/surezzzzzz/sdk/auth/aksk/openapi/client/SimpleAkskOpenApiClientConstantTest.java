package io.github.surezzzzzz.sdk.auth.aksk.openapi.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 常量取值钉子测试
 *
 * <p>配置键被四个传输件 Starter 与调用方配置共同依赖，取值漂移会导致 `aksk-profile`
 * 配置静默失联，这里将字面量钉死。
 *
 * @author surezzzzzz
 */
class SimpleAkskOpenApiClientConstantTest {

    @Test
    @DisplayName("管理 OpenAPI 配置前缀取值固定")
    void testConfigPrefixValueIsPinned() {
        assertEquals("io.github.surezzzzzz.sdk.auth.aksk.openapi.client",
                SimpleAkskOpenApiClientConstant.CONFIG_PREFIX);
    }

    @Test
    @DisplayName("aksk-profile 配置键由前缀派生")
    void testAkskProfileConfigKeyDerivesFromPrefix() {
        assertEquals(SimpleAkskOpenApiClientConstant.CONFIG_PREFIX + ".aksk-profile",
                SimpleAkskOpenApiClientConstant.AKSK_PROFILE_CONFIG_KEY);
    }

    @Test
    @DisplayName("默认 Profile 名称固定为 default")
    void testDefaultProfileValueIsPinned() {
        assertEquals("default", SimpleAkskOpenApiClientConstant.DEFAULT_PROFILE);
    }
}
