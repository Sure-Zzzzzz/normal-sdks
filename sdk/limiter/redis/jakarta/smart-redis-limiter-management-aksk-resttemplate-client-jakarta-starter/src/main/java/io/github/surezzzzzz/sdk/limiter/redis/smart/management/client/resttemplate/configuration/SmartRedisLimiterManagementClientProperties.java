package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流管理客户端 RestTemplate 传输配置
 * <p>聚形态宿主独立配置本客户端；不与运行端 starter 的 remotePolicy 配置耦合，
 * 策略端点、超时与字节上限在此声明，凭据与令牌由注入的 RestTemplate 携带。</p>
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.limiter.management.client")
public class SmartRedisLimiterManagementClientProperties {

    /**
     * 是否启用客户端装配（默认关闭；散形态不引本制品即不装配）
     */
    private Boolean enable = false;

    /**
     * v1 三元组策略快照端点（完整 URL）
     */
    private String policySnapshotUrl;

    /**
     * v2 类型化策略快照端点（完整 URL）
     */
    private String typedPolicySnapshotUrl;

    /**
     * 连接超时（毫秒）
     */
    private Long connectTimeoutMillis = 3000L;

    /**
     * 读取超时（毫秒）
     */
    private Long readTimeoutMillis = 10000L;

    /**
     * 单次响应最大字节数
     */
    private Long maxResponseBytes = 4194304L;
}
