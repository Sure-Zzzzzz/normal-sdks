package io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request;

import lombok.Data;

/**
 * 更新协作组请求
 *
 * @author surezzzzzz
 */
@Data
public class UpdateUserGroupRequest {

    private String name;

    private String description;

    private Integer status;
}
