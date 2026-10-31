package io.github.surezzzzzz.sdk.audit.license.test;

import io.github.surezzzzzz.sdk.audit.license.SimpleLicenseAuditListenerPackage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * License 审计监听器测试应用：只扫审计包与测试包——事件源（license server starter）
 * 的组件装配不进本上下文（监听器消费的是事件对象，不需要 server 在场）。
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
@SpringBootApplication
@ComponentScan(basePackageClasses = {SimpleLicenseAuditListenerPackage.class, LicenseAuditListenerTestApplication.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = TestConfiguration.class))
public class LicenseAuditListenerTestApplication {
}
