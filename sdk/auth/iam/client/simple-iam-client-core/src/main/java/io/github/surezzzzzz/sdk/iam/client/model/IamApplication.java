package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * IAM 可信应用目录投影（applications 族响应模型，1.3.7）：目录三字段，不含 client 明细。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamApplication {

    /**
     * 应用编码。
     */
    String applicationCode;

    /**
     * 应用名称。
     */
    String applicationName;

    /**
     * 状态（1=启用）。
     */
    Integer status;
}
