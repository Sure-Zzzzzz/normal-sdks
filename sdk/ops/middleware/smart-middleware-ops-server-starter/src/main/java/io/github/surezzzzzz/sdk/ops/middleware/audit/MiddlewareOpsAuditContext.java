package io.github.surezzzzzz.sdk.ops.middleware.audit;

import lombok.Builder;
import lombok.Getter;

/**
 * 中间件运维受控请求的完整参数快照。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class MiddlewareOpsAuditContext {

    private final String elasticsearchIndex;
    private final String elasticsearchDsl;
    private final String mysqlSql;
    private final String redisKey;
    private final String redisField;
    private final String kafkaTopic;
    private final String kafkaGroupId;
    private final Integer page;
    private final Integer size;
    private final Long offset;
}
