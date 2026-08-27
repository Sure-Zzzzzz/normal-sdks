package io.github.surezzzzzz.sdk.s3.route.configuration;

import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteAuthenticationType;
import io.github.surezzzzzz.sdk.s3.route.constant.S3RouteSignerType;
import io.github.surezzzzzz.sdk.s3.route.constant.SimpleS3RouteConstant;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S3 Route 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleS3RouteConstant.CONFIG_PREFIX)
public class SimpleS3RouteProperties {

    /**
     * 是否启用 Route。
     */
    private boolean enable;

    /**
     * 关闭时等待已准入请求完成的时长（毫秒）。
     */
    private int shutdownTimeoutMs = SimpleS3RouteConstant.DEFAULT_SHUTDOWN_TIMEOUT_MS;

    /**
     * 精确 targetKey 到固定 S3 兼容对象存储的映射。
     */
    private Map<String, TargetConfig> targets = new LinkedHashMap<String, TargetConfig>();

    /**
     * 单个 target 配置。
     */
    @Data
    @ToString(exclude = {"endpoint", "trustedCaFile", "authentication"})
    public static class TargetConfig {

        /**
         * 固定 S3 兼容对象存储 endpoint 地址。
         */
        private String endpoint;

        /**
         * 签名与寻址使用的 Region（V2 签名不参与签名计算，仍建议如实配置）。
         */
        private String region = SimpleS3RouteConstant.DEFAULT_REGION;

        /**
         * 签名版本，默认 AWS Signature V4；部署为 V2 签名的 S3 兼容存储需显式配置。
         */
        private S3RouteSignerType signerType = S3RouteSignerType.AWS_V4;

        /**
         * 是否启用 Path Style 寻址。
         */
        private boolean pathStyleEnabled = SimpleS3RouteConstant.DEFAULT_PATH_STYLE_ENABLED;

        /**
         * 可信私有 CA 文件路径（PEM / DER，可含多张 CA）；配置时 endpoint 必须为 HTTPS。
         */
        private String trustedCaFile;

        /**
         * target 认证配置。
         */
        private AuthenticationConfig authentication = new AuthenticationConfig();

        /**
         * target 私有客户端配置。
         */
        private ClientConfig client = new ClientConfig();
    }

    /**
     * target 认证配置。
     */
    @Data
    @ToString(exclude = {"accessKey", "secretKey", "sessionToken"})
    public static class AuthenticationConfig {

        /**
         * 认证类型。
         */
        private S3RouteAuthenticationType type = S3RouteAuthenticationType.NONE;

        /**
         * ACCESS_KEY 认证 AccessKey。
         */
        private String accessKey;

        /**
         * ACCESS_KEY 认证 SecretKey。
         */
        private String secretKey;

        /**
         * ACCESS_KEY 认证会话令牌（可选，用于临时凭据）。
         */
        private String sessionToken;
    }

    /**
     * target 私有客户端配置。
     */
    @Data
    public static class ClientConfig {

        /**
         * 建连超时（毫秒）。
         */
        private int connectTimeoutMs = SimpleS3RouteConstant.DEFAULT_CONNECT_TIMEOUT_MS;

        /**
         * 读超时（毫秒）。
         */
        private int socketTimeoutMs = SimpleS3RouteConstant.DEFAULT_SOCKET_TIMEOUT_MS;

        /**
         * 连接池最大连接数。
         */
        private int maxConnections = SimpleS3RouteConstant.DEFAULT_MAX_CONNECTIONS;

        /**
         * 请求级超时（毫秒），0 表示不启用。
         */
        private int requestTimeoutMs = SimpleS3RouteConstant.DEFAULT_REQUEST_TIMEOUT_MS;

        /**
         * 客户端执行级超时（毫秒），0 表示不启用。
         */
        private int clientExecutionTimeoutMs = SimpleS3RouteConstant.DEFAULT_CLIENT_EXECUTION_TIMEOUT_MS;

        /**
         * 连接最大空闲时间（毫秒），空闲超过该时长的连接被回收。
         */
        private int connectionMaxIdleMs = SimpleS3RouteConstant.DEFAULT_CONNECTION_MAX_IDLE_MS;

        /**
         * 连接 TTL（毫秒），-1 表示不限制。
         */
        private long connectionTtlMs = SimpleS3RouteConstant.DEFAULT_CONNECTION_TTL_MS;
    }
}
