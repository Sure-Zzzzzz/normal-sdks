package io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserGroupEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 协作组响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class UserGroupResponse {

    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer status;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * 协作组实体转响应视图
     */
    public static UserGroupResponse from(IamUserGroupEntity group) {
        return new UserGroupResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                group.getDescription(),
                group.getStatus(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
