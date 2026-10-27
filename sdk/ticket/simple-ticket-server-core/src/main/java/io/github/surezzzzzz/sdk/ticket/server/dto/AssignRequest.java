package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分派/转派请求（姓名由服务端经档案解析，不信任请求体）。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRequest {

    /**
     * 目标组编码
     */
    private String groupCode;

    /**
     * 被派人标识
     */
    private String assigneeSubjectId;
}
