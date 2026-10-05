package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.configuration;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XFF Capture 审计 Elasticsearch Provider 配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleXffCaptureAuditEsPersistenceProviderConstant.CONFIG_PREFIX)
public class SimpleXffCaptureAuditEsPersistenceProviderProperties {

    /**
     * 是否启用 Elasticsearch 写入。
     */
    private boolean enable;
}
