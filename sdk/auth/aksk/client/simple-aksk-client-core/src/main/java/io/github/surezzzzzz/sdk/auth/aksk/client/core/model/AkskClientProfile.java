package io.github.surezzzzzz.sdk.auth.aksk.client.core.model;

import lombok.Getter;
import lombok.ToString;

/**
 * 已解析的 AKP Profile 运行时模型
 *
 * <p>对象不可变；secret 不参与 {@code toString()}，也不写入缓存或日志。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
public class AkskClientProfile {

    /**
     * Profile 名称；default 表示顶层默认凭据
     */
    private final String profile;

    /**
     * AKSK Server origin
     */
    private final String serverUrl;

    /**
     * Token 端点
     */
    private final String tokenEndpoint;

    /**
     * AKP Client ID
     */
    private final String clientId;

    /**
     * AKP Client Secret
     */
    @ToString.Exclude
    private final String clientSecret;

    /**
     * Token 请求连接超时（毫秒）
     */
    private final int connectTimeoutMs;

    /**
     * Token 请求读取超时（毫秒）
     */
    private final int readTimeoutMs;

    public AkskClientProfile(String profile, String serverUrl, String tokenEndpoint,
                             String clientId, String clientSecret,
                             int connectTimeoutMs, int readTimeoutMs) {
        this.profile = profile;
        this.serverUrl = serverUrl;
        this.tokenEndpoint = tokenEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }
}
