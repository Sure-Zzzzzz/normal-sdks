package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.constant;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;

/**
 * Simple AKSK RestTemplate HttpSession Client Constants
 *
 * @author surezzzzzz
 */
public final class HttpSessionRestTemplateConstant {

    private HttpSessionRestTemplateConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = SimpleAkskClientCoreConstant.CONFIG_PREFIX + ".resttemplate";

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
