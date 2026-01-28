package io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation;

import java.lang.annotation.*;

/**
 * 要求存在指定字段
 *
 * <p>用于方法或类级别，要求安全上下文中必须存在指定字段。
 *
 * <p>使用示例：
 * <pre>{@code
 * @GetMapping("/user/info")
 * @RequireField("userId")
 * public UserInfo getUserInfo() {
 *     // 此方法要求存在 userId 字段
 *     return ...;
 * }
 *
 * @GetMapping("/tenant/info")
 * @RequireField(value = "tenantId", message = "Tenant ID is required")
 * public TenantInfo getTenantInfo() {
 *     // 此方法要求存在 tenantId 字段，并自定义错误消息
 *     return ...;
 * }
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireField {

    /**
     * 字段名（camelCase 格式）
     *
     * @return 字段名
     */
    String value();

    /**
     * 错误消息（可选）
     *
     * @return 错误消息
     */
    String message() default "";
}
