package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理面策略变更客户端传输配置
 *
 * <p>凭据与令牌由注入的 AKSK 底座 RestTemplate 携带，本配置只声明管理端地址与响应上限。
 * AKP 授权矩阵：变更族（create/update/delete/upsert）要求 {@code smartLimiterPolicy:write} +
 * DATA {@code limiter-policy} write；回读族（findByKey/findByIdentityRow，upsert 编排依赖）
 * 要求 {@code smartLimiterPolicy:read} + DATA read——均按 serviceCode 限定范围。</p>
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client")
public class SmartRedisLimiterPolicyMutationClientProperties {

    /**
     * 是否启用客户端装配（默认关闭；不引本制品或不开开关即不装配）
     */
    private Boolean enable = false;

    /**
     * 管理端根地址（含 context-path，如 https://host/api；深路径由客户端拼接）
     */
    private String baseUrl;

    /**
     * 单次响应最大字节数
     */
    private Long maxResponseBytes = 4194304L;
}
