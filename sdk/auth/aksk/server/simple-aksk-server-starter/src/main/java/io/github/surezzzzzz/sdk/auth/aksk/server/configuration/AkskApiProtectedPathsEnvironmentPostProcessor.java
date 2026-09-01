package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Collections;

/**
 * /api受保护路径默认注入后置处理器。
 * <p>
 * 公共资源层的装配条件是"未配置protected-paths不装配"，本模块的三个REST控制器依赖该链保护——
 * 使用方漏配时请求将掉入default链（permitAll）匿名暴露。此处在环境准备阶段注入默认值{@code /api/**}，
 * 使用方已显式配置时不注入（其值优先）。
 *
 * @author surezzzzzz
 */
public class AkskApiProtectedPathsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * 公共资源层protected-paths配置键。
     */
    public static final String PROTECTED_PATHS_PROPERTY =
            "io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths";

    private static final String PROPERTY_SOURCE_NAME = "akskApiProtectedPathsDefaults";

    /**
     * 注入默认受保护路径。
     *
     * @param environment 可配置环境
     * @param application Spring Boot应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty(PROTECTED_PATHS_PROPERTY)) {
            return;
        }
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (PROPERTY_SOURCE_NAME.equals(propertySource.getName())) {
                return;
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME,
                Collections.<String, Object>singletonMap(PROTECTED_PATHS_PROPERTY, "/api/**")));
    }
}
