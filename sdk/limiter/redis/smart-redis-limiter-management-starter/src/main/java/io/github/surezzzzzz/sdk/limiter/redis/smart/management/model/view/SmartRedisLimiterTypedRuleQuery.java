package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view;

import lombok.Builder;
import lombok.Getter;

/**
 * v2 类型化规则查询条件
 *
 * <p>objectId 为前缀匹配（列表按对象检索），其余字段为精确匹配；
 * 分页语义与 v1 查询一致（page 从 1 起）。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterTypedRuleQuery {

    private String serviceCode;
    private String resourceCode;
    private String dimension;
    private String selector;
    private String namespace;
    private String customType;
    private String objectId;
    private Boolean enabled;
    private Integer page;
    private Integer size;
}
