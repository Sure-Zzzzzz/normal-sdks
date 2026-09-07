package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.SimpleAkskServerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Simple AKSK Server Auto Configuration
 *
 * @author surezzzzzz
 */
@Configuration
// 过期 Token 定时清理任务需要调度基础设施；@EnableScheduling 幂等，宿主已开启调度时无副作用
@EnableScheduling
@EnableConfigurationProperties(SimpleAkskServerProperties.class)
@EnableJpaRepositories(basePackageClasses = SimpleAkskServerPackage.class)
@EntityScan(basePackageClasses = SimpleAkskServerPackage.class)
@ComponentScan(
        basePackageClasses = SimpleAkskServerPackage.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SimpleAkskServerComponent.class
        )
)
public class SimpleAkskServerAutoConfiguration {
}
