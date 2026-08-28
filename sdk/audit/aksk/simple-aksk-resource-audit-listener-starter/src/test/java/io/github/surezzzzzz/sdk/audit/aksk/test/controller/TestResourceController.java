package io.github.surezzzzzz.sdk.audit.aksk.test.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用受保护资源：走公共安全链 + API 权限校验。
 *
 * @author surezzzzzz
 */
@RestController
public class TestResourceController {

    @GetMapping("/api/resource")
    @RequireApiPermission("resource.read")
    public String resource() {
        return "resource";
    }
}
