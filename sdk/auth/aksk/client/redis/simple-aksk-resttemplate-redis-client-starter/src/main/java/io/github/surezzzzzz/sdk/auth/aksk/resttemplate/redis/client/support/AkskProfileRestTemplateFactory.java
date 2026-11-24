package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.support;

import org.springframework.web.client.RestTemplate;

/**
 * AKP Profile RestTemplate 工厂契约
 *
 * @author surezzzzzz
 */
public interface AkskProfileRestTemplateFactory {

    /**
     * 创建命名 Profile 的受保护 RestTemplate
     *
     * @param profile Profile 名称
     * @return RestTemplate
     */
    RestTemplate create(String profile);
}
