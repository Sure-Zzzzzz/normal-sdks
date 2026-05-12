package io.github.surezzzzzz.sdk.limiter.redis.smart.controller;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author: Sure.
 * @description 测试控制器
 * @Date: 2024/12/XX XX:XX
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class TestController {

    /**
     * 注解限流测试（5次/秒）
     */
    @GetMapping("/annotated")
    @SmartRedisLimiter(rules = {
            @SmartRedisLimitRule(count = 5, window = 1, unit = TimeUnit.SECONDS)
    })
    public Map<String, Object> annotatedEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "annotated endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 公共接口（拦截器限流）
     */
    @GetMapping("/public/test")
    public Map<String, Object> publicEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "public endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 审计测试接口（拦截器限流，用于触发限流事件）
     */
    @GetMapping("/public/audit-test")
    public Map<String, Object> auditTestEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "audit test endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 审计通过测试接口（拦截器限流，用于验证通过时不发布事件）
     */
    @GetMapping("/public/audit-pass")
    public Map<String, Object> auditPassEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "audit pass endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 审计详情测试接口（拦截器限流，用于验证Event携带限流详情字段）
     */
    @GetMapping("/public/audit-detail")
    public Map<String, Object> auditDetailEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "audit detail endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 审计始终发布测试接口（拦截器限流，用于验证Event始终发布）
     */
    @GetMapping("/public/audit-always")
    public Map<String, Object> auditAlwaysEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "audit always endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 用户查询接口（GET，各用户独立限流）
     */
    @GetMapping("/user/{userId}")
    public Map<String, Object> userEndpoint(@PathVariable Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "user endpoint");
        result.put("userId", userId);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 用户创建接口（POST，共享限流）
     */
    @PostMapping("/user/{userId}")
    public Map<String, Object> createUser(@PathVariable Long userId) {
        log.info("创建用户: {}", userId);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "user created");
        result.put("userId", userId);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 用户创建接口（POST /api/user，共享限流）
     */
    @PostMapping("/user")
    public Map<String, Object> createUserWithoutId(@RequestBody(required = false) Map<String, Object> body) {
        log.info("创建用户（无ID）: {}", body);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "user created");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 特殊用户接口（精确路径优先级测试）
     */
    @GetMapping("/user/special")
    public Map<String, Object> specialUserEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "special user endpoint");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    // ==================== 滑动窗口测试接口（拦截器模式） ====================

    /**
     * 滑动窗口GET接口（拦截器限流，algorithm=sliding，fallback=allow）
     */
    @GetMapping("/sliding/{id}")
    public Map<String, Object> slidingWindowGet(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "sliding window GET");
        result.put("id", id);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 滑动窗口POST接口（拦截器限流，algorithm=sliding，fallback=deny）
     */
    @PostMapping("/sliding/{id}")
    public Map<String, Object> slidingWindowPost(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "sliding window POST");
        result.put("id", id);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    // ==================== 混用算法测试接口（拦截器模式） ====================

    /**
     * 混用算法GET接口（algorithm=sliding，fallback=allow）
     */
    @GetMapping("/mixed/{id}")
    public Map<String, Object> mixedAlgorithmGet(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "mixed algorithm GET");
        result.put("id", id);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 混用算法POST接口（algorithm=fixed，fallback=deny）
     */
    @PostMapping("/mixed/{id}")
    public Map<String, Object> mixedAlgorithmPost(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "mixed algorithm POST");
        result.put("id", id);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 健康检查接口（排除限流）
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * IP获取测试接口（排除限流）
     */
    @GetMapping("/ip")
    public Map<String, Object> ipEndpoint(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("x-forwarded-for", request.getHeader("X-Forwarded-For"));
        result.put("x-real-ip", request.getHeader("X-Real-IP"));
        result.put("remote-addr", request.getRemoteAddr());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 响应头测试接口（滑动窗口）
     */
    @GetMapping("/sliding/header-pass")
    public Map<String, Object> slidingHeaderPass() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "sliding header test pass");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 响应头测试接口（滑动窗口，拒绝）
     */
    @GetMapping("/sliding/header-reject-{id}")
    public Map<String, Object> slidingHeaderReject(@PathVariable String id) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "sliding header test reject");
        result.put("id", id);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 响应头测试接口（固定窗口）
     */
    @GetMapping("/public/fixed-header")
    public Map<String, Object> fixedHeaderEndpoint() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "fixed header test");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
}
