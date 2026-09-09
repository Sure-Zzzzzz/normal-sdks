package io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request;

import lombok.Data;

/**
 * 创建协作组请求
 *
 * @author surezzzzzz
 */
@Data
public class CreateUserGroupRequest {

    private String code;

    private String name;

    private String description;

    private Integer status;
}
