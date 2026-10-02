package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smart Cache 配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SmartCacheTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.cache.consistency.pubsub-channel-prefix=legacy-prefix"
)
class SmartCachePropertiesTest {

    @Autowired
    private SmartCacheProperties boundProperties;

    @Test
    @DisplayName("测试默认配置符合 2.0.0 设计")
    void shouldUseRouteNativeDefaults() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.validate();

        log.info("默认 keyFormat: {}, pubsubMode: {}", properties.getL2().getKeyFormat(), properties.getPubsub().getMode());
        assertEquals(SmartCacheConstant.DEFAULT_L2_KEY_FORMAT, properties.getL2().getKeyFormat(), "默认 L2 keyFormat 应正确");
        assertEquals(SmartCacheConstant.PUBSUB_MODE_ROUTED, properties.getPubsub().getMode(), "默认 Pub/Sub 应为 routed");
        assertFalse(properties.getRoute().getScanEnabled(), "默认不应开启 Redis 扫描");
        assertTrue(properties.getSerializer().getTrustedPackages().contains(SmartCacheConstant.TRUSTED_PACKAGE_JAVA_LANG),
                "默认可信包应包含 java.lang");
    }

    @Test
    @DisplayName("测试 L1 和 L2 都关闭时自动启用 L1")
    void shouldEnableL1WhenBothLayersDisabled() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getL1().setEnabled(false);
        properties.getL2().setEnabled(false);

        properties.validate();

        assertTrue(properties.getL1().isEnabled(), "L1 和 L2 都关闭时应自动启用 L1");
        assertFalse(properties.getL2().isEnabled(), "L2 配置不应被自动打开");
    }

    @Test
    @DisplayName("测试非法 Pub/Sub 模式回退为 routed")
    void shouldFallbackInvalidPubSubMode() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getPubsub().setMode("bad-mode");

        properties.validate();

        assertEquals(SmartCacheConstant.PUBSUB_MODE_ROUTED, properties.getPubsub().getMode(), "非法 Pub/Sub 模式应回退为 routed");
    }

    @Test
    @DisplayName("非法预热失败策略回退为 continue")
    void shouldFallbackInvalidWarmUpFailurePolicy() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getWarmUp().setFailurePolicy("invalid-policy");

        properties.validate();

        log.info("非法预热失败策略回退结果：{}", properties.getWarmUp().getFailurePolicy());
        assertEquals(SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY, properties.getWarmUp().getFailurePolicy(),
                "非法预热失败策略应回退为 continue");
    }

    @Test
    @DisplayName("空白预热失败策略回退为 continue")
    void shouldFallbackBlankWarmUpFailurePolicy() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getWarmUp().setFailurePolicy("  ");

        properties.validate();

        log.info("空白预热失败策略回退结果：{}", properties.getWarmUp().getFailurePolicy());
        assertEquals(SmartCacheConstant.DEFAULT_WARMUP_FAILURE_POLICY, properties.getWarmUp().getFailurePolicy(),
                "空白预热失败策略应回退为 continue");
    }

    @Test
    @DisplayName("旧 Pub/Sub 前缀配置不再绑定，必须保留新字段默认值")
    void shouldIgnoreRemovedLegacyPubSubChannelPrefix() {
        assertEquals(SmartCacheConstant.DEFAULT_PUBSUB_CHANNEL_PREFIX, boundProperties.getPubsubChannelPrefix(),
                "已删除的旧字段不能覆盖 Pub/Sub 默认前缀");
    }

    @Test
    @DisplayName("仅配置新 Pub/Sub 前缀时应使用新字段")
    void shouldUseNewPubSubChannelPrefix() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.getPubsub().setChannelPrefix("new-prefix");

        properties.validate();

        assertEquals("new-prefix", properties.getPubsubChannelPrefix(), "仅配置新字段时必须使用新前缀");
    }

    @Test
    @DisplayName("未配置 Pub/Sub 前缀时应使用默认值")
    void shouldUseDefaultPubSubChannelPrefix() {
        SmartCacheProperties properties = new SmartCacheProperties();

        properties.validate();

        assertEquals(SmartCacheConstant.DEFAULT_PUBSUB_CHANNEL_PREFIX, properties.getPubsubChannelPrefix(),
                "未配置 Pub/Sub 前缀时必须使用默认值");
    }
}
