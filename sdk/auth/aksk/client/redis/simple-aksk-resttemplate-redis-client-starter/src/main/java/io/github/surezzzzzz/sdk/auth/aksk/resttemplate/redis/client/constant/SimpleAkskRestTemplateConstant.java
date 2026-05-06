package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.constant;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;

/**
 * Simple AKSK RestTemplate Redis Client Constants
 *
 * @author surezzzzzz
 */
public final class SimpleAkskRestTemplateConstant {

    private SimpleAkskRestTemplateConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = SimpleAkskClientCoreConstant.CONFIG_PREFIX + ".resttemplate";

    // ==================== 连接池默认值 ====================

    /**
     * 默认最大连接数
     */
    public static final int DEFAULT_MAX_TOTAL = 100;

    /**
     * 默认每个路由最大连接数
     */
    public static final int DEFAULT_MAX_PER_ROUTE = 20;

    /**
     * 默认连接超时（毫秒）
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    /**
     * 默认读取超时（毫秒）
     */
    public static final int DEFAULT_READ_TIMEOUT_MS = 30000;
}
