package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托目标应用
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamTargetApplication {

    /**
     * 应用 ID。
     */
    Long applicationId;

    /**
     * 应用编码。
     */
    String applicationCode;

    /**
     * 应用名称。
     */
    String name;

    /**
     * 状态。
     */
    Integer status;

    /**
     * 是否内置应用。
     */
    boolean builtIn;
}
