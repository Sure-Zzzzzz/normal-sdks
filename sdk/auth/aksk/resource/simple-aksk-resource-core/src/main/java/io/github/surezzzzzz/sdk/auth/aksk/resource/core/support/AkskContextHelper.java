package io.github.surezzzzzz.sdk.auth.aksk.resource.core.support;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * AKSK 上下文公共工具方法
 *
 * <p>供 resource-server-starter 和 security-context-starter 共用，消除重复代码。
 *
 * @author surezzzzzz
 */
public final class AkskContextHelper {

    private AkskContextHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建 AkskAccessEvent
     *
     * @param caller  事件发布者（this）
     * @param source  验证来源（jwt / introspect / header）
     * @param context 安全上下文 Map
     * @param request 当前 HTTP 请求
     * @return AkskAccessEvent
     */
    public static AkskAccessEvent buildAccessEvent(
            Object caller, String source, Map<String, String> context, HttpServletRequest request) {
        return new AkskAccessEvent(
                caller,
                context.get(SimpleAkskResourceConstant.FIELD_CLIENT_ID),
                context.get(SimpleAkskResourceConstant.FIELD_CLIENT_TYPE),
                context.get(SimpleAkskResourceConstant.FIELD_USER_ID),
                context.get(SimpleAkskResourceConstant.FIELD_USERNAME),
                context.get(SimpleAkskResourceConstant.FIELD_ROLES),
                context.get(SimpleAkskResourceConstant.FIELD_SCOPE),
                request.getRequestURI(),
                request.getMethod(),
                request.getRemoteAddr(),
                request.getHeader(SimpleAkskResourceConstant.HEADER_USER_AGENT),
                source,
                context.get(SimpleAkskResourceConstant.FIELD_TRACE_ID),
                context
        );
    }

    /**
     * 获取当前 HTTP 请求
     *
     * @return 当前 HttpServletRequest，不在请求上下文中时返回 null
     */
    public static HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    /**
     * 将 claim 值转换为字符串
     *
     * <p>List 类型（如 scope）用空格拼接，其他类型直接 toString。
     *
     * @param value claim 值
     * @return 字符串表示
     */
    @SuppressWarnings("unchecked")
    public static String claimValueToString(Object value) {
        if (value instanceof List) {
            return String.join(" ", (List<String>) value);
        }
        return value.toString();
    }
}
