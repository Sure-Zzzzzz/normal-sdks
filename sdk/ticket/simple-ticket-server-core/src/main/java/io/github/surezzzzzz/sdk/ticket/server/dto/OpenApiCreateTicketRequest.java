package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 机器开单请求（openapi 面；Idempotency-Key 走请求头）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiCreateTicketRequest {

    /**
     * 类型编码
     */
    private String typeCode;

    /**
     * 标题
     */
    private String subject;

    /**
     * 描述
     */
    private String description;

    /**
     * 优先级
     */
    private String priority;

    /**
     * 提单人标识（谁的问题就是谁的单，必填）
     */
    private String ownerSubjectId;

    /**
     * 模板答案
     */
    private Map<String, Object> formAnswers;
}
