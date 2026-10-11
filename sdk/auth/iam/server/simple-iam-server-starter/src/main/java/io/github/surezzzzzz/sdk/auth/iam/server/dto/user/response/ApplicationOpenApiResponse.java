package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 开放 API 可信应用目录响应（1.3.7）：目录三字段，不带 client 明细与管理视图字段。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class ApplicationOpenApiResponse {

    /**
     * 应用编码
     */
    private String applicationCode;

    /**
     * 应用名称
     */
    private String applicationName;

    /**
     * 状态（1=启用）
     */
    private Integer status;
}
