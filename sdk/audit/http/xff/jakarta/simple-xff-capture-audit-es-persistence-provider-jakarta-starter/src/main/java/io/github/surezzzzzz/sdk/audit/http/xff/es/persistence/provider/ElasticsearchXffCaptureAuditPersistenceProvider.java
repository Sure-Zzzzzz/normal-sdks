package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.annotation.SimpleXffCaptureAuditEsPersistenceProviderComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 将不可变 XFF 审计文档写入 Elasticsearch Persistence。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleXffCaptureAuditEsPersistenceProviderComponent
public class ElasticsearchXffCaptureAuditPersistenceProvider
        implements XffCaptureAuditPersistenceProvider {

    /**
     * 独立序列化器保证公开审计字段名不受宿主 ObjectMapper 配置影响。
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(MapperFeature.USE_STD_BEAN_NAMING, true)
            .setPropertyNamingStrategy(new LowerCamelCasePropertyNamingStrategy());
    private static final TypeReference<Map<String, Object>> DOCUMENT_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final PersistenceEngine persistenceEngine;

    /**
     * 创建 Elasticsearch Provider。
     *
     * @param persistenceEngine Persistence 写入入口
     */
    public ElasticsearchXffCaptureAuditPersistenceProvider(PersistenceEngine persistenceEngine) {
        this.persistenceEngine = persistenceEngine;
    }

    /**
     * 以事件 ID 为文档 ID，同步提交到固定逻辑索引。
     *
     * @param document 审计文档
     */
    @Override
    public void persist(XffCaptureAuditDocument document) {
        Map<String, Object> payload;
        try {
            payload = OBJECT_MAPPER.convertValue(document, DOCUMENT_TYPE);
            log.debug("XFF 审计字段投影完成：eventId=[{}]", document.getEventId());
        } catch (RuntimeException e) {
            log.debug("XFF 审计字段投影失败：eventId=[{}]，异常类型=[{}]",
                    document.getEventId(), e.getClass().getName());
            throw e;
        }

        log.debug("XFF 审计 ES 写入开始：eventId=[{}]", document.getEventId());
        try {
            persistenceEngine.index(IndexRequest.builder()
                    .index(SimpleXffCaptureAuditEsPersistenceProviderConstant.AUDIT_WRITE_INDEX)
                    .id(document.getEventId()).document(payload).build());
            log.debug("XFF 审计 ES 写入完成：eventId=[{}]", document.getEventId());
        } catch (RuntimeException e) {
            log.debug("XFF 审计 ES 写入失败：eventId=[{}]，异常类型=[{}]",
                    document.getEventId(), e.getClass().getName());
            throw e;
        }
    }

    /**
     * 避免 XRealIP 等缩写属性被 Jackson 转成非契约字段名。
     */
    private static final class LowerCamelCasePropertyNamingStrategy
            extends PropertyNamingStrategies.NamingBase {

        @Override
        public String translate(String propertyName) {
            if (propertyName == null || propertyName.isEmpty()) {
                return propertyName;
            }
            return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
        }
    }
}
