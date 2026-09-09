package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import io.github.surezzzzzz.sdk.auth.iam.server.SimpleIamServerPackage;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Simple IAM Server Auto Configuration
 *
 * @author surezzzzzz
 */
@Configuration
@EnableAspectJAutoProxy
// 过期 Token 定时清理任务需要调度基础设施；@EnableScheduling 幂等，宿主已开启调度时无副作用
@EnableScheduling
@ConditionalOnProperty(prefix = SimpleIamServerConstant.CONFIG_PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SimpleIamServerProperties.class)
@AutoConfigureBefore(SessionAutoConfiguration.class)
@Import({AuthorizationServerConfiguration.class, OAuth2SecurityConfiguration.class,
        ExternalIdentityProviderConfiguration.class,
        IamRedisHttpSessionConfiguration.class})
@EnableJpaRepositories(basePackageClasses = SimpleIamServerPackage.class)
@EntityScan(basePackageClasses = SimpleIamServerPackage.class)
@ComponentScan(
        basePackageClasses = SimpleIamServerPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ANNOTATION,
                classes = SimpleIamServerComponent.class
        )
)
public class SimpleIamServerAutoConfiguration {
}
