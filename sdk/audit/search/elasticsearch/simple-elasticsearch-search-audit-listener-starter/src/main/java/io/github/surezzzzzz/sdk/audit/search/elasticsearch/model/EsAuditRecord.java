package io.github.surezzzzzz.sdk.audit.search.elasticsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ES Audit Record
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsAuditRecord {

    // ==================== 用户信息 ====================

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端类型
     */
    private String clientType;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    // ==================== 资源信息 ====================

    /**
     * 索引别名
     */
    private String indexAlias;

    /**
     * 实际查询的索引列表
     */
    private String[] actualIndices;

    /**
     * 数据源名称
     */
    private String datasource;

    // ==================== 查询信息 ====================

    /**
     * 查询条件
     */
    private String queryCondition;

    /**
     * 命中总数（聚合查询为 null）
     */
    private Long total;

    /**
     * 本次返回条数
     */
    private Integer returnedSize;

    /**
     * 查询耗时（毫秒）
     */
    private Long took;

    // ==================== 元数据 ====================

    /**
     * 事件时间戳
     */
    private Long timestamp;

    /**
     * 链路追踪ID
     */
    private String traceId;
}
