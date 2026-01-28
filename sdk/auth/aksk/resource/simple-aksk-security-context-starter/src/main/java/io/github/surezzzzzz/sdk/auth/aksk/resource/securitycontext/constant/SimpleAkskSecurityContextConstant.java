package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;

/**
 * Simple AKSK Security Context 常量
 *
 * <p>定义 Security Context Starter 使用的常量。
 *
 * <p>字段名常量直接引用 {@link SimpleAkskResourceConstant}，避免重复定义。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class SimpleAkskSecurityContextConstant {

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
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}

