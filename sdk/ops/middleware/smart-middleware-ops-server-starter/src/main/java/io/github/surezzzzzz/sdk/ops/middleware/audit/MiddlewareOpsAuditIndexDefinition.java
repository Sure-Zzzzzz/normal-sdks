package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Middleware Ops 审计文档投影。
 *
 * @author surezzzzzz
 */
public final class MiddlewareOpsAuditIndexDefinition {

    private MiddlewareOpsAuditIndexDefinition() {
        throw new UnsupportedOperationException("审计文档投影不能实例化");
    }

    /**
     * 返回 persistence 写入使用的文档标识。
     *
     * @param requestId 服务端请求标识
     * @return Elasticsearch 文档标识
     */
    public static String documentId(String requestId) {
        return requestId;
    }

    /**
     * 将 Server 审计事件投影为固定字段文档。
     *
     * @param event Server 审计事件
     * @return Elasticsearch 文档
     */
    public static Map<String, Object> document(MiddlewareOpsAuditEvent event) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ID, documentId(event.getRequestId()));
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OCCURRED_AT, event.getOccurredAt().toString());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SUBJECT, event.getSubject());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CAPABILITY,
                event.getCapability() == null ? null : event.getCapability().name());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE,
                event.getMiddlewareType() == null ? null : event.getMiddlewareType().getCode());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DATASOURCE_KEY, event.getDatasourceKey());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CLUSTER_TAG, event.getClusterTag());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_RESOURCE_DIGEST, event.getResourceDigest());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_HTTP_STATUS, event.getStatus());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DURATION_MILLIS, event.getDurationMillis());
        appendContext(document, event.getContext());
        return Collections.unmodifiableMap(document);
    }

    private static void appendContext(Map<String, Object> document, MiddlewareOpsAuditContext context) {
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_INDEX,
                context == null ? null : context.getElasticsearchIndex());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_DSL,
                context == null ? null : context.getElasticsearchDsl());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MYSQL_SQL,
                context == null ? null : context.getMysqlSql());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_KEY,
                context == null ? null : context.getRedisKey());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_FIELD,
                context == null ? null : context.getRedisField());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_TOPIC,
                context == null ? null : context.getKafkaTopic());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_GROUP_ID,
                context == null ? null : context.getKafkaGroupId());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_PAGE,
                context == null ? null : context.getPage());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SIZE,
                context == null ? null : context.getSize());
        document.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OFFSET,
                context == null ? null : context.getOffset());
    }
}
