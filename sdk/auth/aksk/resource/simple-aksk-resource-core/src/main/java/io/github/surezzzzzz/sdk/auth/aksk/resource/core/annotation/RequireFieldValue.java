package io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation;

import java.lang.annotation.*;

/**
 * 要求字段值匹配
 *
 * <p>用于方法或类级别，要求安全上下文中指定字段的值必须匹配。
 *
 * <p>使用示例：
 * <pre>{@code
 * @GetMapping("/admin/users")
 * @RequireFieldValue(field = "role", value = "admin")
 * public List<User> listUsers() {
 *     // 此方法要求 role 字段值为 "admin"
 *     return ...;
 * }
 *
 * @GetMapping("/tenant/data")
 * @RequireFieldValue(field = "tenantId", value = "tenant-123", message = "Access denied")
 * public TenantData getTenantData() {
 *     // 此方法要求 tenantId 字段值为 "tenant-123"
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
public @interface RequireFieldValue {

    /**
     * 字段名（camelCase 格式）
     *
     * @return 字段名
     */
    String field();

    /**
     * 期望的字段值
     *
     * @return 期望的字段值
     */
    String value();

    /**
     * 错误消息（可选）
     *
     * @return 错误消息
     */
    String message() default "";
}
