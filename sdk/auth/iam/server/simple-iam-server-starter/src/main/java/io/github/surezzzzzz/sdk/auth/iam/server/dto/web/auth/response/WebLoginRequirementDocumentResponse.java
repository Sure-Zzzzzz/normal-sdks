package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录要求文档版本展示模型。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebLoginRequirementDocumentResponse {

    private Long versionId;

    private String code;

    private String title;

    private String url;

    private String content;

    private boolean required;
}
