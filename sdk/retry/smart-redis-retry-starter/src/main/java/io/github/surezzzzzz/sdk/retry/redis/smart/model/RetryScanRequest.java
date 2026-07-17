package io.github.surezzzzzz.sdk.retry.redis.smart.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 重试记录扫描请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryScanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用于选择 Redis 数据源的路由 Key
     */
    private String routeKey;
    /**
     * 待扫描的重试类型
     */
    private String retryType;
    /**
     * 扫描游标；Standalone 使用 Redis 原生游标，Cluster 使用引擎返回的不透明游标。
     */
    private String cursor;
    /**
     * 单个 Redis 节点单次 SCAN 建议返回的记录数
     */
    private Integer count;
    /**
     * 是否同时加载重试状态
     */
    private boolean includeInfo;
}
