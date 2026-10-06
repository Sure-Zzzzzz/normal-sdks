package io.github.surezzzzzz.sdk.audit.aksk.test.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试受保护资源，由公共资源安全链与 API 权限校验共同保护。
 *
 * @author surezzzzzz
 */
@RestController
public class TestResourceController {
    /**
     * 返回已授权访问结果。
     */
    @GetMapping("/api/resource")
    @RequireApiPermission("resource.read")
    public String resource() {
        return "resource";
    }
}
