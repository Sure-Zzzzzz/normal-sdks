package io.github.surezzzzzz.sdk.limiter.redis.smart.controller;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
}
