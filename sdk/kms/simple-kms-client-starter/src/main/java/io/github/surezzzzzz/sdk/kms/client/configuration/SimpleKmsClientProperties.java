package io.github.surezzzzzz.sdk.kms.client.configuration;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple KMS Client 配置。
 *
 * <p>仅承载 传输 和载荷边界；认证凭据、租户身份与任何密钥材料必须通过受控扩展点提供，不得写入此配置。</p>
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKmsClientConstant.CONFIG_PREFIX)
public class SimpleKmsClientProperties {

    /**
     * 是否启用默认 HTTP Client；默认关闭，已有自定义 Client 不受此开关影响。
     */
    private Boolean enabled = SimpleKmsClientConstant.DEFAULT_ENABLED;
    /**
     * KMS 服务 origin，只允许 http/https 根地址，不能包含路径、查询、片段或用户信息。
     */
    private String baseUrl;
    /**
     * 连接池最大连接数。
     */
    private Integer maxTotal = SimpleKmsClientConstant.DEFAULT_MAX_TOTAL;
    /**
     * 单个路由最大连接数。
     */
    private Integer maxPerRoute = SimpleKmsClientConstant.DEFAULT_MAX_PER_ROUTE;
    /**
     * 建连超时毫秒数。
     */
    private Integer connectTimeoutMillis = SimpleKmsClientConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS;
    /**
     * 从连接池取得连接的超时毫秒数。
     */
    private Integer connectionRequestTimeoutMillis = SimpleKmsClientConstant.DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS;
    /**
     * 读取超时毫秒数。
     */
    private Integer readTimeoutMillis = SimpleKmsClientConstant.DEFAULT_READ_TIMEOUT_MILLIS;
    /**
     * 最大请求体字节数。
     */
    private Integer maxRequestBytes = SimpleKmsClientConstant.DEFAULT_MAX_REQUEST_BYTES;
    /**
     * 最大响应体字节数。
     */
    private Integer maxResponseBytes = SimpleKmsClientConstant.DEFAULT_MAX_RESPONSE_BYTES;
}
