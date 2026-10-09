package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 受委托角色 wire 模型（revision=If-Match 值来源）
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamOpenRoleResponse {

    /**
     * 角色 UUID。
     */
    public String openRoleId;

    /**
     * 幂等 UUID。
     */
    public String externalId;

    /**
     * 编码。
     */
    public String code;

    /**
     * 目标应用。
     */
    public Long applicationId;

    /**
     * 授权根。
     */
    public Long rootDepartmentId;

    /**
     * 名称。
     */
    public String name;

    /**
     * 描述。
     */
    public String description;

    /**
     * 修订版本。
     */
    public long revision;

    /**
     * 状态。
     */
    public String state;

    /**
     * 已设规则。
     */
    public Boolean rulePresent;
}
