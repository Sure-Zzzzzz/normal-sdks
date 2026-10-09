package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.AkskOpenApiClient;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client.annotation.SimpleAkskOpenApiRestTemplateComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * 默认客户端装配（经自动配置的组件扫描注册；底座 RestTemplate 缺失时启动即报缺 Bean，响亮失败不静默）。
 *
 * @author surezzzzzz
 */
@SimpleAkskOpenApiRestTemplateComponent
public class DefaultAkskOpenApiClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DefaultAkskOpenApiClientConfiguration.class);

    /**
     * 装配默认管理 OpenAPI 客户端（目标地址复用底座 server-url；宿主声明同类型 Bean 即可整体替换默认实现）。
     *
     * @param akskClientRestTemplate 底座认证 RestTemplate（底座装配，拦截器挂 AKSK 令牌链）
     * @param serverUrl              AKSK Server 地址（底座配置键）
     * @return 默认实现 Bean
     */
    @Bean
    @ConditionalOnMissingBean(AkskOpenApiClient.class)
    public DefaultAkskOpenApiClient akskOpenApiClient(
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate,
            @Value("${" + io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant
                    .SimpleAkskOpenApiClientConstant.BASE_CONFIG_PREFIX + ".server-url:}") String serverUrl) {
        log.info("AKSK OpenAPI RestTemplate 客户端装配：目标={}", serverUrl);
        return new DefaultAkskOpenApiClient(akskClientRestTemplate, serverUrl);
    }
}
