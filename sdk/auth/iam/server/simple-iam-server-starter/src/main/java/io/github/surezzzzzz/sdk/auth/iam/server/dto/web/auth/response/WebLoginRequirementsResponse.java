package io.github.surezzzzzz.sdk.auth.iam.server.dto.web.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 当前部署的登录要求集合。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class WebLoginRequirementsResponse {

    /**
     * 当前状态：DISABLED、REQUIRED 或 ERROR。
     */
    private String mode;

    private boolean required;

    private String revision;

    private List<WebLoginRequirementDocumentResponse> documents;
}
