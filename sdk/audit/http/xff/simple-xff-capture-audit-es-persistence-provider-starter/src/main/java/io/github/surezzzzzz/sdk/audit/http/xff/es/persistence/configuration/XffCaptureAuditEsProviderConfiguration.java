package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.configuration;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.SimpleXffCaptureAuditEsPersistenceProviderPackage;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.annotation.SimpleXffCaptureAuditEsPersistenceProviderComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple XFF Capture Audit Elasticsearch Persistence Provider 自动配置。
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(SimpleElasticsearchPersistenceAutoConfiguration.class)
@EnableConfigurationProperties(SimpleXffCaptureAuditEsPersistenceProviderProperties.class)
@ConditionalOnProperty(prefix = SimpleXffCaptureAuditEsPersistenceProviderConstant.CONFIG_PREFIX,
        name = SimpleXffCaptureAuditEsPersistenceProviderConstant.CONFIG_ENABLE,
        havingValue = SimpleXffCaptureAuditEsPersistenceProviderConstant.CONFIG_VALUE_TRUE)
@ComponentScan(
        basePackageClasses = SimpleXffCaptureAuditEsPersistenceProviderPackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleXffCaptureAuditEsPersistenceProviderComponent.class)
)
public class XffCaptureAuditEsProviderConfiguration {
}
