package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author: Sure.
 * @description 降级策略测试（细粒度降级）
 * @Date: 2024/12/XX XX:XX
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
@AutoConfigureMockMvc
public class SmartLimiterFallbackTest {

    private RedisConnectionFactory originalFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestService testService;

    @Autowired
    @Qualifier("smartRedisLimiterRedisTemplate")
    private RedisTemplate<String, String> smartRedisLimiterRedisTemplate;

    @BeforeEach
    public void setup() throws Exception {
        log.info("=== 测试前准备 ===");

        // 清理Redis数据
        try {
            Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                smartRedisLimiterRedisTemplate.delete(keys);
                log.info("清理了 {} 个限流key", keys.size());
            }
        } catch (Exception e) {
            log.warn("清理Redis数据失败: {}", e.getMessage());
        }
    }

    @AfterEach
    public void tearDown() {
        // 确保每个测试后都恢复正常连接
        if (originalFactory != null) {
            smartRedisLimiterRedisTemplate.setConnectionFactory(originalFactory);
            originalFactory = null;
            log.info("已恢复原始Redis连接工厂");
        }
    }

    /**
     * 模拟Redis不可用：将连接工厂切换到不存在的端口
     */
    private void stopRedisAndCloseConnection() throws Exception {
        log.info("模拟Redis不可用：切换到不存在的端口 16379...");
        originalFactory = smartRedisLimiterRedisTemplate.getConnectionFactory();
        LettuceConnectionFactory brokenFactory = new LettuceConnectionFactory("localhost", 16379);
        brokenFactory.afterPropertiesSet();
        smartRedisLimiterRedisTemplate.setConnectionFactory(brokenFactory);
        Thread.sleep(200);
    }

    /**
     * 恢复Redis连接：切回原始连接工厂
     */
    private void startRedisAndInitConnection() throws Exception {
        log.info("恢复Redis连接...");
        if (originalFactory != null) {
            smartRedisLimiterRedisTemplate.setConnectionFactory(originalFactory);
            originalFactory = null;
        }
        Thread.sleep(200);
    }

    /**
     * 测试1：拦截器规则级别降级 - GET请求放行
     */
    @Test
    public void testInterceptorRuleLevelFallbackAllow() throws Exception {
        log.info("=== 测试拦截器规则级别降级 - GET请求放行 ===");

        // 验证Redis正常时限流生效
        log.info("验证Redis正常时限流生效...");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/test"))
                    .andExpect(status().isOk());
        }

        // 第6次被限流
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isTooManyRequests());

        // 停止Redis
        stopRedisAndCloseConnection();

        // 验证GET请求使用规则级别fallback=allow，应该放行
        log.info("验证Redis异常时GET请求放行（规则级别fallback=allow）...");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/public/test"))
                    .andExpect(status().isOk());
        }

        log.info("=== 拦截器规则级别降级 - GET请求放行测试通过 ===");
    }

    /**
     * 测试2：拦截器规则级别降级 - POST请求拒绝
     */
    @Test
    public void testInterceptorRuleLevelFallbackDeny() throws Exception {
        log.info("=== 测试拦截器规则级别降级 - POST请求拒绝 ===");

        // 验证Redis正常时可以访问
        log.info("验证Redis正常时POST请求正常...");
        mockMvc.perform(post("/api/user/123"))
                .andExpect(status().isOk());

        // 停止Redis
        stopRedisAndCloseConnection();

        // 验证POST请求使用规则级别fallback=deny，应该拒绝
        log.info("验证Redis异常时POST请求拒绝（规则级别fallback=deny）...");
        mockMvc.perform(post("/api/user/123"))
                .andDo(print())
                .andExpect(status().isTooManyRequests());

        log.info("=== 拦截器规则级别降级 - POST请求拒绝测试通过 ===");
    }

    /**
     * 测试3：拦截器模式默认降级策略
     */
    @Test
    public void testInterceptorDefaultFallback() throws Exception {
        log.info("=== 测试拦截器模式默认降级策略 ===");

        // /api/user/123 规则没有配置fallback，应该使用拦截器默认（allow）

        // 验证Redis正常时限流生效
        log.info("验证Redis正常时限流生效...");
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(get("/api/user/123"))
                    .andExpect(status().isOk());
        }

        // 第16次被限流
        mockMvc.perform(get("/api/user/123"))
                .andExpect(status().isTooManyRequests());

        // 停止Redis
        stopRedisAndCloseConnection();

        // 验证使用拦截器默认降级策略（allow）
        log.info("验证Redis异常时使用拦截器默认降级策略（allow）...");
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/user/123"))
                    .andExpect(status().isOk());
        }

        log.info("=== 拦截器模式默认降级策略测试通过 ===");
    }

    /**
     * 测试4：注解模式默认降级策略
     */
    @Test
    public void testAnnotationDefaultFallback() throws Exception {
        log.info("=== 测试注解模式默认降级策略 ===");

        // 验证Redis正常时可以访问
        String result = testService.limitedMethod("test");
        assertEquals("success", result);

        // 停止Redis
        stopRedisAndCloseConnection();

        // 验证使用注解模式默认降级策略（allow）
        log.info("验证Redis异常时使用注解模式默认降级策略（allow）...");
        for (int i = 0; i < 10; i++) {
            String fallbackResult = testService.limitedMethod("test-fallback-" + i);
            assertEquals("success", fallbackResult);
        }

        log.info("=== 注解模式默认降级策略测试通过 ===");
    }

    /**
     * 测试6：Redis恢复后限流恢复正常
     */
    @Test
    public void testRedisRecovery() throws Exception {
        log.info("=== 测试Redis恢复后限流恢复正常 ===");

        // 停止Redis
        stopRedisAndCloseConnection();

        // 降级期间请求成功（使用规则级别fallback=allow）
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isOk());

        // 重启Redis
        startRedisAndInitConnection();

        // 清理数据
        Set<String> keys = smartRedisLimiterRedisTemplate.keys(SmartRedisLimiterRedisKeyConstant.KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            smartRedisLimiterRedisTemplate.delete(keys);
        }

        // 验证限流恢复正常
        log.info("验证限流恢复...");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/public/test"))
                    .andExpect(status().isOk());
        }

        // 第6次应该被限流
        mockMvc.perform(get("/api/public/test"))
                .andExpect(status().isTooManyRequests());

        log.info("=== Redis恢复后限流恢复正常测试通过 ===");
    }

    /**
     * 测试7：降级策略优先级
     */
    @Test
    public void testFallbackPriority() throws Exception {
        log.info("=== 测试降级策略优先级 ===");

        // 规则级别 > 模式默认 > 全局默认

        // 1. POST请求使用规则级别fallback=deny（最高优先级）
        stopRedisAndCloseConnection();

        mockMvc.perform(post("/api/user/123"))
                .andExpect(status().isTooManyRequests());  // deny

        startRedisAndInitConnection();

        // 2. /api/user/123 GET使用拦截器默认fallback=allow（中优先级）
        stopRedisAndCloseConnection();

        mockMvc.perform(get("/api/user/123"))
                .andExpect(status().isOk());  // allow

        log.info("=== 降级策略优先级测试通过 ===");
    }
}
