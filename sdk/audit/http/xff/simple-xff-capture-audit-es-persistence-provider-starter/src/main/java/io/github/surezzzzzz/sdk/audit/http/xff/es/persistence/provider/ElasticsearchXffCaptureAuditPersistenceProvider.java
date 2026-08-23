package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.annotation.SimpleXffCaptureAuditEsPersistenceProviderComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;

import java.util.Map;

/**
 * XFF Capture 审计 Elasticsearch Persistence Provider。
 *
 * @author surezzzzzz
 */
@SimpleXffCaptureAuditEsPersistenceProviderComponent
public class ElasticsearchXffCaptureAuditPersistenceProvider
        implements XffCaptureAuditPersistenceProvider {

    /**
     * Provider 自维护的序列化器，不使用 Spring Bean，保证审计写入不受应用 JSON 配置影响。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(MapperFeature.USE_STD_BEAN_NAMING, true)
            .setPropertyNamingStrategy(new LowerCamelCasePropertyNamingStrategy());
    private final PersistenceEngine persistenceEngine;

    /**
     * 创建 Elasticsearch Provider。
     *
     * @param persistenceEngine Persistence Engine
     */
    public ElasticsearchXffCaptureAuditPersistenceProvider(PersistenceEngine persistenceEngine) {
        this.persistenceEngine = persistenceEngine;
    }

    /**
     * 同步写入固定逻辑索引。
     *
     * @param document 审计文档
     */
    @Override
    public void persist(XffCaptureAuditDocument document) {
        persistenceEngine.index(IndexRequest.builder()
                .index(SimpleXffCaptureAuditEsPersistenceProviderConstant.AUDIT_WRITE_INDEX)
                .id(document.getEventId())
                .document(OBJECT_MAPPER.convertValue(document, Map.class))
                .build());
    }

    /**
     * 保留公开审计字段的首字母小写命名，避免 XRealIP 等缩写字段产生非契约名称。
     */
    private static final class LowerCamelCasePropertyNamingStrategy
            extends PropertyNamingStrategy.PropertyNamingStrategyBase {

        @Override
        public String translate(String propertyName) {
            if (propertyName == null || propertyName.isEmpty()) {
                return propertyName;
            }
            return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
        }
    }
}
