package io.github.surezzzzzz.sdk.ops.middleware.kafka.datasource;

import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;

/**
 * Kafka 数据源清单请求。
 *
 * @author surezzzzzz
 */
public class KafkaDatasourceListRequest implements MiddlewareOpsRequest {

    private final boolean auditRequired;

    public KafkaDatasourceListRequest() {
        this(true);
    }

    private KafkaDatasourceListRequest(boolean auditRequired) {
        this.auditRequired = auditRequired;
    }

    /**
     * 创建概览自动加载请求。
     *
     * @return 不写审计的概览请求
     */
    public static KafkaDatasourceListRequest forOverview() {
        return new KafkaDatasourceListRequest(false);
    }

    @Override
    public MiddlewareOpsCapability getCapability() {
        return MiddlewareOpsCapability.KAFKA_DATASOURCE_LIST;
    }

    @Override
    public String getDatasourceKey() {
        return "all";
    }

    @Override
    public String getResourceScope() {
        return "datasource-list";
    }

    @Override
    public boolean isAuditRequired() {
        return auditRequired;
    }
}
