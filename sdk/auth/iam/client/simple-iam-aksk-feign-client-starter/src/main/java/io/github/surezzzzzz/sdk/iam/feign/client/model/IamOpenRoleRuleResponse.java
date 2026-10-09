package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 受委托角色规则 wire 模型
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamOpenRoleRuleResponse {

    /**
     * 角色 UUID。
     */
    public String openRoleId;

    /**
     * 目标应用。
     */
    public Long applicationId;

    /**
     * 修订版本。
     */
    public long revision;

    /**
     * 页面权限码。
     */
    public java.util.List<String> pagePermissions;

    /**
     * API 权限码。
     */
    public java.util.List<String> apiPermissions;

    /**
     * DATA 模板。
     */
    public java.util.Map<String, Object> dataGrantTemplate;
}
