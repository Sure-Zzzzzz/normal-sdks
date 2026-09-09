package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM Web 验证码挑战端点限流测试
 *
 * <p>主测试 yml 的 limiter 段为 /captcha 配置 30 次/分钟/IP（rules[1]，rules[0] 为 /csrf），
 * 挑战生成含图片渲染与 Redis 挑战 key 写入，IP 级限流防匿名刷取；全量对 /captcha 的
 * GET 调用（出口均为 127.0.0.1）共享配额但不触顶。本类 {@code @TestPropertySource}
 * 压低阈值（2 次/秒）验证 429 机制。两个用例各自以 X-Forwarded-For 声明不同 IP
 * 隔离配额（IP 策略取值链 XFF 优先），避免相互消耗。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.limiter.redis.smart.mode=interceptor",
        // List 绑定以高优先级属性源的索引集为准：inline 声明 rules 必须从 [0] 起完整覆盖，
        // 否则 yml 的 rules[0]（csrf）与 inline 的 rules[1] 跨源交错触发 unbound
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].path-pattern=/iam/web/auth/csrf",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].method=GET",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].resource-code=iam-web-csrf",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].key-strategy=ip",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].algorithm=sliding",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].fallback=allow",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].limits[0].count=60",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].limits[0].window=1",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[0].limits[0].unit=MINUTES",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].path-pattern=/iam/web/auth/captcha",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].method=GET",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].resource-code=iam-web-captcha",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].key-strategy=ip",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].algorithm=sliding",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].fallback=allow",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].limits[0].count=2",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].limits[0].window=1",
        "io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.rules[1].limits[0].unit=SECONDS"
})
class IamWebAuthCaptchaRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("同 IP 超过阈值后 /captcha 返回 429 并携带 Retry-After：挑战刷取有配额")
    void shouldRejectCaptchaWith429WhenSameIpExceedsLimit() throws Exception {
        String ip = "198.51.100.21";
        MvcResult first = mockMvc.perform(get("/iam/web/auth/captcha").header("X-Forwarded-For", ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaId").isNotEmpty())
                .andReturn();
        MvcResult second = mockMvc.perform(get("/iam/web/auth/captcha").header("X-Forwarded-For", ip))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(200, first.getResponse().getStatus());
        assertEquals(200, second.getResponse().getStatus());

        MvcResult third = mockMvc.perform(get("/iam/web/auth/captcha").header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andReturn();
        String retryAfter = third.getResponse().getHeader("Retry-After");
        assertNotNull(retryAfter, "429 响应必须携带 Retry-After 头");
        assertTrue(Long.parseLong(retryAfter) >= 0);
        log.info("验证码限流触发：前两次 200，第三次 429（Retry-After={}s）", retryAfter);
    }

    @Test
    @DisplayName("不同 IP 配额相互独立：第二 IP 与本地回环不受触顶 IP 连带")
    void shouldIsolateQuotaPerIp() throws Exception {
        String otherIp = "203.0.113.27";
        mockMvc.perform(get("/iam/web/auth/captcha").header("X-Forwarded-For", otherIp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaId").isNotEmpty());
        mockMvc.perform(get("/iam/web/auth/captcha").header("X-Forwarded-For", otherIp))
                .andExpect(status().isOk());

        // 无 XFF 回退 remoteAddr（127.0.0.1），与上述 IP 配额互不干扰
        mockMvc.perform(get("/iam/web/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captchaId").isNotEmpty());
    }
}
