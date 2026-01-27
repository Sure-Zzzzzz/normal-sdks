package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation;

import java.lang.annotation.*;

/**
 * 要求 SpEL 表达式为 true
 *
 * <p>用于方法或类级别，要求 SpEL 表达式计算结果为 true。
 *
 * <p>表达式中可以使用 #context 访问安全上下文 Map。
 *
 * <p>使用示例：
 * <pre>{@code
 * @GetMapping("/admin/users")
 * @RequireExpression("#context['role'] == 'admin'")
 * public List<User> listUsers() {
 *     // 此方法要求 role 字段值为 "admin"
 *     return ...;
 * }
 *
 * @GetMapping("/tenant/data")
 * @RequireExpression(value = "#context['tenantId'] != null && #context['tenantId'].startsWith('tenant-')",
 *                    message = "Invalid tenant ID")
 * public TenantData getTenantData() {
 *     // 此方法要求 tenantId 字段不为空且以 "tenant-" 开头
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
public @interface RequireExpression {

    /**
     * SpEL 表达式
     *
     * @return SpEL 表达式
     */
    String value();

    /**
     * 错误消息（可选）
     *
     * @return 错误消息
     */
    String message() default "";
}
