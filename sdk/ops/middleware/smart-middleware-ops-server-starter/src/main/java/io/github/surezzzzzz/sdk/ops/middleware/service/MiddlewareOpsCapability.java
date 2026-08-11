package io.github.surezzzzzz.sdk.ops.middleware.service;

import lombok.Getter;

/**
 * 一期显式只读能力。
 *
 * @author surezzzzzz
 */
@Getter
public enum MiddlewareOpsCapability {

    ELASTICSEARCH_DATASOURCE_CATALOG(MiddlewareType.ELASTICSEARCH),
    REDIS_DATASOURCE_CATALOG(MiddlewareType.REDIS),
    KAFKA_DATASOURCE_CATALOG(MiddlewareType.KAFKA),
    MYSQL_DATASOURCE_CATALOG(MiddlewareType.MYSQL),
    ELASTICSEARCH_SUMMARY(MiddlewareType.ELASTICSEARCH),
    ELASTICSEARCH_INDEX_LIST(MiddlewareType.ELASTICSEARCH),
    ELASTICSEARCH_DOCUMENT_QUERY(MiddlewareType.ELASTICSEARCH),
    REDIS_DATASOURCE_LIST(MiddlewareType.REDIS),
    REDIS_SUMMARY(MiddlewareType.REDIS),
    REDIS_KEY_METADATA(MiddlewareType.REDIS),
    REDIS_KEY_READ(MiddlewareType.REDIS),
    KAFKA_DATASOURCE_LIST(MiddlewareType.KAFKA),
    KAFKA_TOPIC_LIST(MiddlewareType.KAFKA),
    KAFKA_TOPIC_RUNTIME(MiddlewareType.KAFKA),
    KAFKA_CONSUMER_GROUP_LIST(MiddlewareType.KAFKA),
    KAFKA_CONSUMER_GROUP_LAG_LIST(MiddlewareType.KAFKA),
    MYSQL_DATASOURCE_STATUS(MiddlewareType.MYSQL),
    MYSQL_SELECT(MiddlewareType.MYSQL);

    private final MiddlewareType middlewareType;

    MiddlewareOpsCapability(MiddlewareType middlewareType) {
        this.middlewareType = middlewareType;
    }
}
