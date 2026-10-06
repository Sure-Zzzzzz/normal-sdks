package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 批准目标应用的最小只读信息。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class OpenTargetApplicationResponse {
    /**
     * 应用编号。
     */
    private final Long applicationId;
    /**
     * 应用编码。
     */
    private final String applicationCode;
    /**
     * 展示名称。
     */
    private final String name;
    /**
     * 当前状态。
     */
    private final Integer status;
    /**
     * 是否内置；本族不允许操作内置应用。
     */
    private final boolean builtIn;
}
