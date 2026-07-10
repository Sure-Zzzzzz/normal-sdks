package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * ES Persistence 审计记录
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsPersistenceAuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 客户端类型
     */
    private String clientType;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 链路追踪 ID
     */
    private String traceId;

    /**
     * 事件时间戳
     */
    private Long timestamp;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * persistence 操作类型
     */
    private String operationType;

    /**
     * 请求类型
     */
    private String requestType;

    /**
     * 操作结果
     */
    private String result;

    /**
     * 目标索引
     */
    private String index;

    /**
     * 数据源 key
     */
    private String datasource;

    /**
     * 文档 ID
     */
    private String documentId;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 是否存在冲突
     */
    private Boolean conflict;

    /**
     * 是否部分完成
     */
    private Boolean partial;

    /**
     * 是否客户端异步
     */
    private Boolean clientAsync;

    /**
     * 是否被 route async-write 接管
     */
    private Boolean routeAsyncWrite;

    /**
     * 是否服务端异步任务
     */
    private Boolean serverAsyncTask;

    /**
     * 服务端任务 ID
     */
    private String taskId;

    /**
     * bulk item 总数
     */
    private Integer bulkItemCount;

    /**
     * bulk 成功数
     */
    private Integer bulkSucceeded;

    /**
     * bulk 失败数
     */
    private Integer bulkFailed;

    /**
     * bulk 批次数
     */
    private Integer batchTotal;

    /**
     * bulk 成功批次数
     */
    private Integer batchSucceeded;

    /**
     * bulk 失败批次数
     */
    private Integer batchFailed;

    /**
     * byQuery 总处理数
     */
    private Long total;

    /**
     * byQuery 更新数
     */
    private Long updated;

    /**
     * byQuery 删除数
     */
    private Long deleted;

    /**
     * byQuery 版本冲突数
     */
    private Long versionConflicts;

    /**
     * 开始时间
     */
    private Long startTimeMs;

    /**
     * 耗时（毫秒）
     */
    private Long tookMs;

    /**
     * SDK 错误码
     */
    private String errorCode;

    /**
     * 异常类名
     */
    private String errorClass;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 失败明细
     */
    private List<EsPersistenceAuditFailureRecord> failureList;
}
