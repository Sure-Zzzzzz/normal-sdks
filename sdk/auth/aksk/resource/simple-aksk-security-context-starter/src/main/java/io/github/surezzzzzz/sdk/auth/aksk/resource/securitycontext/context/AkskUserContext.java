package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.context;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant.SimpleAkskSecurityContextConstant;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AKSK 用户上下文
 *
 * <p>提供便捷的静态 API 访问用户信息。
 *
 * <p>基于 Request Attribute 实现，线程池安全，无内存泄漏风险。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 获取用户 ID
 * String userId = AkskUserContext.getUserId();
 *
 * // 获取用户名
 * String username = AkskUserContext.getUsername();
 *
 * // 获取角色列表
 * List<String> roles = AkskUserContext.getRoles();
 *
 * // 获取任意字段
 * String tenantId = AkskUserContext.get("tenantId");
 *
 * // 获取数组字段
 * List<String> permissions = AkskUserContext.getList("permissions");
 * }</pre>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class AkskUserContext {

    /**
     * 获取用户 ID
     *
     * @return 用户 ID，如果不存在则返回 null
     */
    public static String getUserId() {
        return get(SimpleAkskSecurityContextConstant.HEADER_USER_ID);
    }

    /**
     * 获取用户名
     *
     * @return 用户名，如果不存在则返回 null
     */
    public static String getUsername() {
        return get(SimpleAkskSecurityContextConstant.HEADER_USERNAME);
    }

    /**
     * 获取 Client ID
     *
     * @return Client ID，如果不存在则返回 null
     */
    public static String getClientId() {
        return get(SimpleAkskSecurityContextConstant.HEADER_CLIENT_ID);
    }

    /**
     * 获取角色列表
     *
     * @return 角色列表，如果不存在则返回空列表
     */
    public static List<String> getRoles() {
        return getList(SimpleAkskSecurityContextConstant.HEADER_ROLES);
    }

    /**
     * 获取 Scope 列表
     *
     * @return Scope 列表，如果不存在则返回空列表
     */
    public static List<String> getScope() {
        return getList(SimpleAkskSecurityContextConstant.HEADER_SCOPE);
    }

    /**
     * 获取 security_context
     *
     * @return security_context 字符串，如果不存在则返回 null
     */
    public static String getSecurityContext() {
        return get(SimpleAkskSecurityContextConstant.HEADER_SECURITY_CONTEXT);
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
        return (Map<String, String>) request.getAttribute(SimpleAkskSecurityContextConstant.CONTEXT_ATTRIBUTE);
    }

    /**
     * 设置安全上下文（内部使用，不建议外部调用）
     *
     * <p>此方法由 AkskSecurityContextFilter 调用，用于注入安全上下文。
     * <p>外部代码不应直接调用此方法。
     *
     * @param context 安全上下文 Map
     */
    public static void setContext(Map<String, String> context) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            request.setAttribute(SimpleAkskSecurityContextConstant.CONTEXT_ATTRIBUTE, context);
        }
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

    private AkskUserContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
