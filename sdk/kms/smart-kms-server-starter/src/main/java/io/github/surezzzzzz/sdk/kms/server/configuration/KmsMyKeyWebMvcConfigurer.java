package io.github.surezzzzzz.sdk.kms.server.configuration;

import io.github.surezzzzzz.sdk.kms.server.support.KmsMyKeyHttpMessageConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 在默认 KMS 路由装配期间优先注册限定类型转换器，不接管宿主其他类型。
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class KmsMyKeyWebMvcConfigurer implements WebMvcConfigurer {

    private final KmsMyKeyHttpMessageConverter converter;

    /**
     * 将限定类型转换器移至首位，保留宿主其余转换器及相对顺序。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Boot 可能已收集转换器 Bean；只调整优先级，不重复注册同一实例。
        converters.remove(converter);
        converters.add(0, converter);
    }
}
