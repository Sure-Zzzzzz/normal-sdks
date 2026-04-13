package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation.SimpleAkskResourceCoreComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.SimpleAkskServerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Simple AKSK Server Auto Configuration
 *
 * @author surezzzzzz
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(SimpleAkskServerProperties.class)
@EnableJpaRepositories(basePackageClasses = SimpleAkskServerPackage.class)
@EntityScan(basePackageClasses = SimpleAkskServerPackage.class)
@ComponentScans({
        @ComponentScan(
                basePackageClasses = SimpleAkskServerPackage.class,
                includeFilters = @ComponentScan.Filter(
                        type = FilterType.ANNOTATION,
                        classes = SimpleAkskServerComponent.class
                )
        ),
        @ComponentScan(
                basePackages = "io.github.surezzzzzz.sdk.auth.aksk.resource.core",
                includeFilters = @ComponentScan.Filter(
                        type = FilterType.ANNOTATION,
                        classes = SimpleAkskResourceCoreComponent.class
                )
        )
})
public class SimpleAkskServerAutoConfiguration {
}
