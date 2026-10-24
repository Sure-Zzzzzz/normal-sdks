package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Portal 机器路由目录分页响应（1.3.8）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class PortalMachineApplicationPageResponse {

    /**
     * 全局目录版本。
     */
    Long catalogRevision;
    /**
     * 目录总条数。
     */
    long totalElements;
    /**
     * 当前页码（0 起）。
     */
    int page;
    /**
     * 当前页大小。
     */
    int size;
    /**
     * 当前页路由投影。
     */
    List<PortalMachineApplicationResponse> items;
}
