package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 降级策略端到端测试
 *
 * <p>application.yaml 通过 Redis Route 将专用 fallback key 路由到坏端口
 * datasource（localhost:16399 无人监听），让请求真实走生产执行器的 fallback 路径，
 * 验证 Redis/route 异常时按 allow/deny 策略处理。
 *
 * @author Sure.
 * @Date: 2024/12/XX XX:XX
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
@AutoConfigureMockMvc
public class SmartLimiterFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试1：拦截器规则级别降级 - GET请求放行
     */
    @Test
    public void testInterceptorRuleLevelFallbackAllow() throws Exception {
        log.info("=== 测试拦截器规则级别降级 - GET请求放行 ===");

        log.info("验证Redis异常时GET请求放行（规则级别fallback=allow）...");
        mockMvc.perform(get("/api/fallback/allow"))
                .andExpect(status().isOk());

        log.info("=== 拦截器规则级别降级 - GET请求放行测试通过 ===");
    }

    /**
     * 测试2：拦截器规则级别降级 - POST请求拒绝
     */
    @Test
    public void testInterceptorRuleLevelFallbackDeny() throws Exception {
        log.info("=== 测试拦截器规则级别降级 - POST请求拒绝 ===");

        log.info("验证Redis异常时POST请求拒绝（规则级别fallback=deny）...");
        mockMvc.perform(post("/api/fallback/deny"))
                .andExpect(status().isTooManyRequests());

        log.info("=== 拦截器规则级别降级 - POST请求拒绝测试通过 ===");
    }

    /**
     * 测试3：拦截器模式默认降级策略（allow）
     */
    @Test
    public void testInterceptorDefaultFallback() throws Exception {
        log.info("=== 测试拦截器模式默认降级策略 ===");

        log.info("验证Redis异常时使用拦截器默认降级策略（allow）...");
        mockMvc.perform(get("/api/fallback/default"))
                .andExpect(status().isOk());

        log.info("=== 拦截器模式默认降级策略测试通过 ===");
    }

    /**
     * 测试4：降级策略优先级 - 规则级 deny 高于模式默认
     */
    @Test
    public void testFallbackPriority() throws Exception {
        log.info("=== 测试降级策略优先级 ===");

        log.info("POST请求使用规则级别fallback=deny，应拒绝");
        mockMvc.perform(post("/api/fallback/deny"))
                .andExpect(status().isTooManyRequests());

        log.info("GET请求使用规则级别fallback=allow，应放行");
        mockMvc.perform(get("/api/fallback/allow"))
                .andExpect(status().isOk());

        log.info("=== 降级策略优先级测试通过 ===");
    }
}
