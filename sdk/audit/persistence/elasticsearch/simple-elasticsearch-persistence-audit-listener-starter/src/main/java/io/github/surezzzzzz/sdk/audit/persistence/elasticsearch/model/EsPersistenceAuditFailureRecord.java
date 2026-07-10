package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ES Persistence 审计失败明细记录
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsPersistenceAuditFailureRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * item 下标
     */
    private Integer itemIndex;

    /**
     * 操作类型
     */
    private String type;

    /**
     * 目标索引
     */
    private String index;

    /**
     * 文档 ID
     */
    private String id;

    /**
     * HTTP 状态码
     */
    private Integer status;

    /**
     * 状态文本
     */
    private String statusText;

    /**
     * SDK 错误码
     */
    private String errorCode;

    /**
     * ES 错误类型
     */
    private String errorType;

    /**
     * ES 错误原因
     */
    private String errorReason;

    /**
     * 是否建议重试
     */
    private Boolean retryable;

    /**
     * 是否冲突失败
     */
    private Boolean conflict;
}
