package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * typed.gates YAML 绑定验证：管理端「等价配置」面板生成的配置树
 * （io.github.surezzzzzz.sdk.limiter.redis.smart 前缀）必须能被运行端
 * SmartRedisLimiterProperties 原样绑定——面板展示形态与真实配置的契约钉子。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SmartRedisLimiterTypedYamlBindingTest.BindConfiguration.class,
        properties = {
                "io.github.surezzzzzz.sdk.limiter.redis.smart.me=aksk",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.enabled=true",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.expected-policy-epoch=1",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.gates[0].dimension=CREDENTIAL",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.gates[0].namespace=credential",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.gates[0].limits[0].count=10",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.gates[0].limits[0].window=1",
                "io.github.surezzzzzz.sdk.limiter.redis.smart.typed.gates[0].limits[0].unit=MINUTES"
        })
class SmartRedisLimiterTypedYamlBindingTest {

    @org.springframework.boot.test.context.TestConfiguration
    @EnableConfigurationProperties(SmartRedisLimiterProperties.class)
    static class BindConfiguration {
    }

    @Autowired
    private SmartRedisLimiterProperties properties;

    @Test
    void panelYamlTreeBindsToRuntimeProperties() {
        // 前缀逐段对齐 CONFIG_PREFIX（含 smart 段），me 读到即证明树根正确
        assertEquals("aksk", properties.getMe());
        assertTrue(properties.getTyped().getEnabled());
        assertEquals(1L, properties.getTyped().getExpectedPolicyEpoch());
        assertEquals(1, properties.getTyped().getGates().size());
        SmartRedisLimiterProperties.TypedGateConfig gate = properties.getTyped().getGates().get(0);
        assertEquals("CREDENTIAL", gate.getDimension());
        assertEquals("credential", gate.getNamespace());
        assertEquals(1, gate.getLimits().size());
        assertEquals(10L, gate.getLimits().get(0).getCount());
        assertEquals(1L, gate.getLimits().get(0).getWindow());
        assertEquals("MINUTES", gate.getLimits().get(0).getUnit().name());
    }
}
