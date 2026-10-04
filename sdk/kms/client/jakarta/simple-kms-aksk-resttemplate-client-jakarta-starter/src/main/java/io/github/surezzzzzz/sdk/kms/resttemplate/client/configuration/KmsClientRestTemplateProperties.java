package io.github.surezzzzzz.sdk.kms.resttemplate.client.configuration;

import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KMS Client RestTemplate 配置。
 *
 * <p>仅承载 KMS 服务地址；连接池与超时由 aksk 底座 {@code akskClientRestTemplate} 的配置
 * （{@code io.github.surezzzzzz.sdk.auth.aksk.client.resttemplate.*}）管理，本模块不重复定义。</p>
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleKmsClientConstant.CONFIG_PREFIX)
public class KmsClientRestTemplateProperties {

    /**
     * 是否启用本模块（键名对齐 aksk 家族）。
     */
    private Boolean enable = SimpleKmsClientConstant.DEFAULT_ENABLED;
    /**
     * KMS 服务 origin，只允许 http/https 根地址，不能包含路径、查询、片段或用户信息。
     */
    private String baseUrl;
}
