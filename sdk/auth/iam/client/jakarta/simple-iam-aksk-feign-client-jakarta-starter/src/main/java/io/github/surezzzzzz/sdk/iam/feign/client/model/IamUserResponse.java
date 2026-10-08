package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * IAM 用户 wire 模型
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamUserResponse {

    /**
     * 用户数字 ID。
     */
    public Long id;

    /**
     * 登录名。
     */
    public String username;

    /**
     * 显示名。
     */
    public String displayName;

    /**
     * 所属部门 ID。
     */
    public Long departmentId;

    /**
     * 部门名称。
     */
    public String departmentName;

    /**
     * 状态。
     */
    public Integer status;

    /**
     * 邮箱。
     */
    public String email;

    /**
     * 手机号（E.164）。
     */
    public String phone;

    /**
     * 角色编码列表。
     */
    public java.util.List<String> roles;
}
