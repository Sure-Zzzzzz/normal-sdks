package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 协作组成员 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamUserGroupMemberResponse {
    /**
     * 成员公开主体。
     */
    public String subjectId;
    /**
     * 用户名。
     */
    public String username;
    /**
     * 显示名。
     */
    public String displayName;
    /**
     * 状态。
     */
    public Integer status;
}
