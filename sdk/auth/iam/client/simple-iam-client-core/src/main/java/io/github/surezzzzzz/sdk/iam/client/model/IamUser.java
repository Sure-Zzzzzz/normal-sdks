package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * IAM 用户投影（users 族响应模型）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamUser {

    /**
     * 用户数字 ID（内部标识，对外定位用 subjectId）。
     */
    Long id;

    /**
     * 登录名。
     */
    String username;

    /**
     * 显示名。
     */
    String displayName;

    /**
     * 所属部门 ID（null=未挂部门）。
     */
    Long departmentId;

    /**
     * 所属部门名称。
     */
    String departmentName;

    /**
     * 状态。
     */
    Integer status;

    /**
     * 邮箱。
     */
    String email;

    /**
     * 手机号（E.164 规范化形态；null=未登记）。
     */
    String phone;

    /**
     * 角色编码列表。
     */
    java.util.List<String> roles;
}
