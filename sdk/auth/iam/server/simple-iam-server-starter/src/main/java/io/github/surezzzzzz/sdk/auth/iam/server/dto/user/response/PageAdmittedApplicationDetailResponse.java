package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 页面准入应用详情响应（1.3.7）：displayName 与门户侧边栏同源（实体 applicationName）。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class PageAdmittedApplicationDetailResponse {

    /**
     * 应用编码
     */
    private String code;

    /**
     * 应用显示名（与门户侧边栏同源）
     */
    private String displayName;

    /**
     * 应用描述（可空）
     */
    private String description;
}
