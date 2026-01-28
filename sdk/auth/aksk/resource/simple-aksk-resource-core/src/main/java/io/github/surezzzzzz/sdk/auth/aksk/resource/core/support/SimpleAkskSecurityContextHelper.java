package io.github.surezzzzzz.sdk.auth.aksk.resource.core.support;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Simple AKSK Security Context Helper
 *
 * <p>提供便捷的静态 API 访问安全上下文信息。
 *
 * <p>基于 Request Attribute 实现，线程池安全，无内存泄漏风险。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 获取用户 ID
 * String userId = SimpleAkskSecurityContextHelper.getUserId();
 *
 * // 获取用户名
 * String username = SimpleAkskSecurityContextHelper.getUsername();
 *
 * // 获取角色列表
 * List<String> roles = SimpleAkskSecurityContextHelper.getRoles();
 *
 * // 获取任意字段
 * String tenantId = SimpleAkskSecurityContextHelper.get("tenantId");
 *
 * // 获取数组字段
 * List<String> permissions = SimpleAkskSecurityContextHelper.getList("permissions");
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class SimpleAkskSecurityContextHelper {

    /**
     * 获取用户 ID
     *
     * @return 用户 ID，如果不存在则返回 null
     */
    public static String getUserId() {
        return get(SimpleAkskResourceConstant.FIELD_USER_ID);
    }

    /**
     * 获取用户名
     *
     * @return 用户名，如果不存在则返回 null
     */
    public static String getUsername() {
        return get(SimpleAkskResourceConstant.FIELD_USERNAME);
    }

    /**
     * 获取 Client ID
     *
     * @return Client ID，如果不存在则返回 null
     */
    public static String getClientId() {
        return get(SimpleAkskResourceConstant.FIELD_CLIENT_ID);
    }

    /**
     * 获取 Client Type
     *
     * @return Client Type，如果不存在则返回 null
     */
    public static String getClientType() {
        return get(SimpleAkskResourceConstant.FIELD_CLIENT_TYPE);
    }

    /**
     * 获取角色列表
     *
     * @return 角色列表，如果不存在则返回空列表
     */
    public static List<String> getRoles() {
        return getList(SimpleAkskResourceConstant.FIELD_ROLES);
    }

    /**
     * 获取 Scope 列表
     *
     * @return Scope 列表，如果不存在则返回空列表
     */
    public static List<String> getScope() {
        return getList(SimpleAkskResourceConstant.FIELD_SCOPE);
    }

    /**
     * 获取 security_context
     *
     * @return security_context 字符串，如果不存在则返回 null
     */
    public static String getSecurityContext() {
        return get(SimpleAkskResourceConstant.FIELD_SECURITY_CONTEXT);
    }

    /**
     * 获取指定字段的值
     *
     * @param key 字段名（camelCase 格式）
     * @return 字段值，如果不存在则返回 null
     */
    public static String get(String key) {
        Map<String, String> context = getAll();
        return context != null ? context.get(key) : null;
    }

    /**
     * 获取数组字段
     *
     * <p>自动提取数组字段（如 roles0, roles1 → ["admin", "operator"]）
     *
     * @param prefix 数组字段前缀（如 "roles"）
     * @return 数组值列表，如果不存在则返回空列表
     */
    public static List<String> getList(String prefix) {
        Map<String, String> context = getAll();
        if (context == null || context.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        int index = 0;
        while (true) {
            String key = prefix + index;
            String value = context.get(key);
            if (value == null) {
                break;
            }
            result.add(value);
            index++;
        }
        return result;
    }

    /**
     * 获取所有字段
     *
     * @return 所有字段的 Map，如果不存在则返回 null
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> getAll() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return null;
        }
        return (Map<String, String>) request.getAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE);
    }

    /**
     * 获取当前请求
     *
     * @return 当前 HttpServletRequest，如果不存在则返回 null
     */
    private static HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    private SimpleAkskSecurityContextHelper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
