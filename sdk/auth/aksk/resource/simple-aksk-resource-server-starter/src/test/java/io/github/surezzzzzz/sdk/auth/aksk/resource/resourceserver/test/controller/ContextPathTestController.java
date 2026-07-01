package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Context path 测试控制器
 *
 * @author surezzzzzz
 */
@RestController
public class ContextPathTestController {

    /**
     * context-path 专项测试接口
     *
     * @return 测试响应
     */
    @GetMapping("/context-path/basic")
    public Map<String, Object> basic() {
        return buildResponse("context-path-basic");
    }

    /**
     * 公开接口
     *
     * @return 测试响应
     */
    @GetMapping("/public/ping")
    public Map<String, Object> publicPing() {
        return buildResponse("public-pong");
    }

    /**
     * 私有接口
     *
     * @return 测试响应
     */
    @GetMapping("/private/ping")
    public Map<String, Object> privatePing() {
        return buildResponse("private-pong");
    }

    /**
     * 普通 health 路径测试接口，不引入 Actuator
     *
     * @return 测试响应
     */
    @GetMapping("/actuator/health")
    public Map<String, Object> fakeHealth() {
        return buildResponse("up");
    }

    /**
     * 无 context-path 时的 /api 兼容测试接口
     *
     * @return 测试响应
     */
    @GetMapping("/api/legacy-protected")
    public Map<String, Object> legacyProtected() {
        return buildResponse("legacy-protected");
    }

    private Map<String, Object> buildResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        return response;
    }
}
