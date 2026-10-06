package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * 受委托角色事实；删除墓碑不带原角色展示或关系信息。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenRoleResponse {
    /**
     * 对外稳定身份。
     */
    private final String openRoleId;
    /**
     * 创建幂等键。
     */
    private final String externalId;
    /**
     * IAM 生成的普通角色编码。
     */
    private final String code;
    /**
     * 固定应用编号。
     */
    private final Long applicationId;
    /**
     * 固定部门根。
     */
    private final Long rootDepartmentId;
    /**
     * 展示名称。
     */
    private final String name;
    /**
     * 展示描述。
     */
    private final String description;
    /**
     * 当前修改版本。
     */
    private final long revision;
    /**
     * ACTIVE 或 DELETED。
     */
    private final String state;
    /**
     * 是否存在固定应用规则。
     */
    private final Boolean rulePresent;
    /**
     * 当前角色部门关系总数，不代表全部位于根子树。
     */
    private final Long departmentBindingCount;
}
