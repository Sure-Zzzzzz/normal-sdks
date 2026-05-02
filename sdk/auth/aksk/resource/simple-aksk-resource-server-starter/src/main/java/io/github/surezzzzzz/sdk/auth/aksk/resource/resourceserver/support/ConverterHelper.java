package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.support.AkskContextHelper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converter 公共工具方法
 *
 * <p>供 {@link io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskJwtAuthenticationConverter}
 * 和 {@link io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskIntrospectionAuthenticationConverter}
 * 共用。
 *
 * <p>通用方法（buildAccessEvent、getCurrentRequest、claimValueToString）委托给
 * {@link AkskContextHelper}；resource-server-starter 专属方法（extractAuthorities）保留在此。
 *
 * @author surezzzzzz
 */
public final class ConverterHelper {

    private ConverterHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建 AkskAccessEvent，委托给 AkskContextHelper
     *
     * @param caller  事件发布者（this）
     * @param source  验证来源（jwt / introspect）
     * @param context 安全上下文 Map
     * @param request 当前 HTTP 请求
     * @return AkskAccessEvent
     */
    public static AkskAccessEvent buildAccessEvent(
            Object caller, String source, Map<String, String> context, HttpServletRequest request) {
        return AkskContextHelper.buildAccessEvent(caller, source, context, request);
    }

    /**
     * 获取当前 HTTP 请求，委托给 AkskContextHelper
     *
     * @return 当前 HttpServletRequest，不在请求上下文中时返回 null
     */
    public static HttpServletRequest getCurrentRequest() {
        return AkskContextHelper.getCurrentRequest();
    }

    /**
     * 将 scope 字符串转换为 GrantedAuthority 集合
     *
     * @param scope 空格分隔的 scope 字符串（如 "read write"），为 null 或空时返回空集合
     * @return GrantedAuthority 集合
     */
    public static Collection<GrantedAuthority> extractAuthorities(String scope) {
        if (scope == null || scope.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(scope.split(" "))
                .filter(s -> !s.isEmpty())
                .map(s -> new SimpleGrantedAuthority(SimpleAkskResourceConstant.AUTHORITY_SCOPE_PREFIX + s))
                .collect(Collectors.toList());
    }

    /**
     * 将 claim 值转换为字符串，委托给 AkskContextHelper
     *
     * @param value claim 值
     * @return 字符串表示
     */
    public static String claimValueToString(Object value) {
        return AkskContextHelper.claimValueToString(value);
    }
}
