package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamOpenRoleLongDeserializer;
import lombok.Data;

/**
 * 创建固定应用及部门根的受委托角色。
 *
 * @author surezzzzzz
 */
@Data
public class CreateOpenRoleRequest {
    /**
     * 调用方持久保存的创建幂等 UUID。
     */
    private String externalId;
    /**
     * 批准的非内置目标应用。
     */
    @JsonDeserialize(using = IamOpenRoleLongDeserializer.class)
    private Long applicationId;
    /**
     * 批准的客户部门根。
     */
    @JsonDeserialize(using = IamOpenRoleLongDeserializer.class)
    private Long rootDepartmentId;
    /**
     * 角色展示名称。
     */
    private String name;
    /**
     * 可选展示描述。
     */
    private String description;
}
