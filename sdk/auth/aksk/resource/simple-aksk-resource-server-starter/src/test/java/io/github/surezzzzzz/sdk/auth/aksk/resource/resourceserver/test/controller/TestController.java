package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.controller;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation.*;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.support.SimpleAkskSecurityContextHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试控制器
 *
 * <p>提供各种测试接口，用于验证 JWT 认证和权限注解功能
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 无注解接口 - 测试基本功能
     */
    @GetMapping("/basic")
    public Map<String, Object> basic() {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", SimpleAkskSecurityContextHelper.getUserId());
        result.put("username", SimpleAkskSecurityContextHelper.getUsername());
        result.put("clientId", SimpleAkskSecurityContextHelper.getClientId());
        result.put("roles", SimpleAkskSecurityContextHelper.getRoles());
        result.put("scope", SimpleAkskSecurityContextHelper.getScope());
        result.put("allContext", SimpleAkskSecurityContextHelper.getAll());
        return result;
    }

    /**
     * @RequireContext 注解接口 - 测试上下文校验
     */
    @GetMapping("/require-context")
    @RequireContext
    public Map<String, Object> requireContext() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Context exists");
        result.put("userId", SimpleAkskSecurityContextHelper.getUserId());
        return result;
    }

    /**
     * @RequireField 注解接口 - 测试字段存在校验
     */
    @GetMapping("/require-field")
    @RequireField("userId")
    public Map<String, Object> requireField() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Field userId exists");
        result.put("userId", SimpleAkskSecurityContextHelper.getUserId());
        return result;
    }

    /**
     * @RequireFieldValue 注解接口 - 测试字段值匹配校验
     */
    @GetMapping("/require-field-value")
    @RequireFieldValue(field = "clientType", value = "service")
    public Map<String, Object> requireFieldValue() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Client type is service");
        result.put("clientType", SimpleAkskSecurityContextHelper.get("clientType"));
        return result;
    }

    /**
     * @RequireExpression 注解接口 - 测试 SpEL 表达式校验
     */
    @GetMapping("/require-expression")
    @RequireExpression("#context['userId'] != null && #context['userId'].length() > 0")
    public Map<String, Object> requireExpression() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Expression passed");
        result.put("userId", SimpleAkskSecurityContextHelper.getUserId());
        return result;
    }

    /**
     * 测试自定义字段获取
     */
    @GetMapping("/custom-fields")
    public Map<String, Object> customFields() {
        Map<String, Object> result = new HashMap<>();
        result.put("tenantId", SimpleAkskSecurityContextHelper.get("tenantId"));
        result.put("orgId", SimpleAkskSecurityContextHelper.get("orgId"));
        result.put("customField", SimpleAkskSecurityContextHelper.get("customField"));
        return result;
    }

    /**
     * 测试 getSecurityContext()
     */
    @GetMapping("/security-context")
    public Map<String, Object> securityContext() {
        Map<String, Object> result = new HashMap<>();
        result.put("securityContext", SimpleAkskSecurityContextHelper.getSecurityContext());
        return result;
    }

    /**
     * 管理员专属接口 - 只有 admin 角色可以访问
     */
    @GetMapping("/admin/dashboard")
    @RequireExpression("#context['scope'] != null && #context['scope'].contains('admin')")
    public Map<String, Object> adminDashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Welcome to admin dashboard");
        result.put("userId", SimpleAkskSecurityContextHelper.getUserId());
        result.put("scope", SimpleAkskSecurityContextHelper.getScope());
        return result;
    }

    /**
     * 多租户隔离 - 只能访问自己租户的数据
     */
    @GetMapping("/tenant/data")
    @RequireExpression("#context['tenantId'] != null && #context['tenantId'].length() > 0")
    public Map<String, Object> tenantData() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Tenant data access");
        result.put("tenantId", SimpleAkskSecurityContextHelper.get("tenantId"));
        result.put("userId", SimpleAkskSecurityContextHelper.getUserId());
        return result;
    }
}
