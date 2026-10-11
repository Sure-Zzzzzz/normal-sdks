package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 页面准入应用详情（page-admitted-application-details 族响应模型，1.3.7）：
 * displayName 与门户侧边栏同源。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class PageAdmittedApplication {

    /**
     * 应用编码。
     */
    String code;

    /**
     * 应用显示名（与门户侧边栏同源）。
     */
    String displayName;

    /**
     * 应用描述（可空）。
     */
    String description;
}
