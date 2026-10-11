package io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 开放 API 协作组成员响应（1.3.7）：公开主体标识定位，不带内部 userId。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class IamOpenApiGroupUserResponse {

    /**
     * 对外公开主体标识
     */
    private String subjectId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 显示名
     */
    private String displayName;

    /**
     * 状态（1=启用）
     */
    private Integer status;
}
