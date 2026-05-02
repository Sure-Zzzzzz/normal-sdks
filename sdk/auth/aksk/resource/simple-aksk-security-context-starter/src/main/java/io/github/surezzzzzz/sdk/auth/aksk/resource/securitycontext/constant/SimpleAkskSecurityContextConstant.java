package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;

/**
 * Simple AKSK Security Context 常量
 *
 * <p>仅包含 security-context-starter 专属常量。
 * 通用常量（ACCESS_SOURCE_HEADER、FIELD_TRACE_ID、HEADER_USER_AGENT 等）已统一定义在
 * {@link SimpleAkskResourceConstant}。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public final class SimpleAkskSecurityContextConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.resource.security-context";

    /**
     * Filter 名称
     */
    public static final String FILTER_NAME = "akskSecurityContextFilter";

    /**
     * 默认 Header 前缀（引用 core 包常量）
     */
    public static final String DEFAULT_HEADER_PREFIX = SimpleAkskResourceConstant.DEFAULT_HEADER_PREFIX;

    private SimpleAkskSecurityContextConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
