package io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation;

import java.lang.annotation.*;

/**
 * 要求存在安全上下文
 *
 * <p>用于方法或类级别，要求请求中必须存在安全上下文。
 *
 * <p>使用示例：
 * <pre>{@code
 * @RestController
 * @RequireContext
 * public class UserController {
 *     // 所有方法都要求存在安全上下文
 * }
 *
 * @GetMapping("/user/info")
 * @RequireContext
 * public UserInfo getUserInfo() {
 *     // 此方法要求存在安全上下文
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
public @interface RequireContext {

    /**
     * 错误消息（可选）
     *
     * @return 错误消息
     */
    String message() default "Security context is required";
}
