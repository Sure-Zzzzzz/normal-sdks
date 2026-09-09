package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.branding.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Web 品牌信息响应（登录前公开可读，前端据此渲染品牌名/Logo/自定义资源）
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebBrandingResponse {

    private String name;

    private String logoUrl;

    private String customCssUrl;

    private String customJsUrl;
}
