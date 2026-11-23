package io.github.surezzzzzz.sdk.auth.aksk.client.core.transport;

import org.springframework.web.client.RestTemplate;

/**
 * AKSK RestTemplate 解析契约，供业务传输件消费，避免直接依赖底座装配件类型。
 *
 * @author surezzzzzz
 */
public interface AkskRestTemplateResolver {

    /**
     * 按业务配置的 AKP Profile 解析 RestTemplate。
     *
     * @param profile Profile 名称；空或 default 使用默认模板
     * @return RestTemplate
     */
    RestTemplate resolve(String profile);
}
