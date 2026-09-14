package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request;

import lombok.Data;

import java.util.List;

/**
 * Portal 应用根节点全量排序快照写请求。
 */
@Data
public class PortalApplicationOrderRequest {

    /**
     * Portal 全局配置版本；过期快照不得覆盖其他管理员的修改。
     */
    private Long version;

    /**
     * 全部 Portal 集成的应用 ID，数组位置即期望顺序。
     */
    private List<Long> applicationIds;
}
