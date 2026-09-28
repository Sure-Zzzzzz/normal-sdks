package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.SimpleAkskServerPackage;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChangePage;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Collections;
import java.util.List;

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

    /**
     * 没有可选协作适配器时保持本地模式；OWNER_INHERITED 仍然失败关闭。
     */
    @Bean
    @ConditionalOnMissingBean(OwnerAuthorizationProvider.class)
    public OwnerAuthorizationProvider unavailableOwnerAuthorizationProvider() {
        return new OwnerAuthorizationProvider() {
            @Override
            public OwnerAuthorizationReadResult resolve(OwnerAuthorizationKey owner, Long targetApplicationId) {
                return OwnerAuthorizationReadResult.inactive();
            }

            @Override
            public List<OwnerAuthorizationCandidate> listCandidates(OwnerAuthorizationKey owner) {
                return Collections.emptyList();
            }

            @Override
            public OwnerAuthorizationChangePage pullChanges(Long afterSequence, int pageSize) {
                return OwnerAuthorizationChangePage.unavailable();
            }
        };
    }
}
