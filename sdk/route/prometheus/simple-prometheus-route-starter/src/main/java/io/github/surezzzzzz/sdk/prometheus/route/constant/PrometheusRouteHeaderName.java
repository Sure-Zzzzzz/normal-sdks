package io.github.surezzzzzz.sdk.prometheus.route.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Route 管理的请求头名称。
 *
 * @author surezzzzzz
 */
public final class PrometheusRouteHeaderName {

    // ==================== Route 管理的请求头 ====================

    /**
     * Authorization 请求头。
     */
    public static final String AUTHORIZATION = "authorization";

    /**
     * Host 请求头。
     */
    public static final String HOST = "host";

    /**
     * Connection 请求头。
     */
    public static final String CONNECTION = "connection";

    /**
     * Keep-Alive 请求头。
     */
    public static final String KEEP_ALIVE = "keep-alive";

    /**
     * Proxy-Authenticate 请求头。
     */
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";

    /**
     * Proxy-Authorization 请求头。
     */
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";

    /**
     * TE 请求头。
     */
    public static final String TE = "te";

    /**
     * Trailer 请求头。
     */
    public static final String TRAILER = "trailer";

    /**
     * Transfer-Encoding 请求头。
     */
    public static final String TRANSFER_ENCODING = "transfer-encoding";

    /**
     * Upgrade 请求头。
     */
    public static final String UPGRADE = "upgrade";

    /**
     * Content-Length 请求头。
     */
    public static final String CONTENT_LENGTH = "content-length";

    /**
     * Expect 请求头。
     */
    public static final String EXPECT = "expect";

    /**
     * Cookie 请求头。
     */
    public static final String COOKIE = "cookie";

    /**
     * Accept-Encoding 请求头。
     */
    public static final String ACCEPT_ENCODING = "accept-encoding";

    /**
     * 调用方不允许覆盖的请求头集合。
     */
    public static final Set<String> FORBIDDEN_REQUEST_HEADERS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(AUTHORIZATION, HOST, CONNECTION, KEEP_ALIVE, PROXY_AUTHENTICATE,
                    PROXY_AUTHORIZATION, TE, TRAILER, TRANSFER_ENCODING, UPGRADE, CONTENT_LENGTH, EXPECT,
                    COOKIE, ACCEPT_ENCODING)));

    private PrometheusRouteHeaderName() {
    }
}
