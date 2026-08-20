package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.provider;

import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.annotation.SimpleXffCaptureAuditEsPersistenceProviderComponent;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.constant.SimpleXffCaptureAuditEsPersistenceProviderConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;

/**
 * XFF Capture 审计 Elasticsearch Persistence Provider。
 *
 * @author surezzzzzz
 */
@SimpleXffCaptureAuditEsPersistenceProviderComponent
public class ElasticsearchXffCaptureAuditPersistenceProvider
        implements XffCaptureAuditPersistenceProvider {

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
                .document(document)
                .build());
    }
}
