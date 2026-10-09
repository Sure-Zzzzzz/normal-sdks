package io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration;

import io.github.surezzzzzz.sdk.iam.client.constant.SimpleIamClientConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IAM Client RestTemplate 传输配置。
 *
 * <p>仅承载 IAM 服务地址；连接池与超时由 aksk 底座 {@code akskClientRestTemplate} 的配置管理，
 * 本模块不重复定义。</p>
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleIamClientConstant.CONFIG_PREFIX)
public class SimpleIamClientRestTemplateProperties {

    /**
     * 是否启用本模块（键名对齐 aksk 家族）。
     */
    private Boolean enable = Boolean.FALSE;
    /**
     * IAM 服务 origin，只允许 http/https 根地址，不能包含路径、查询、片段或用户信息。
     */
    private String baseUrl;
}
