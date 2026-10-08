package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托角色固定应用规则
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenRoleRule {

    /**
     * 角色 UUID。
     */
    String openRoleId;

    /**
     * 目标应用 ID。
     */
    Long applicationId;

    /**
     * 修订版本（If-Match 值来源）。
     */
    long revision;

    /**
     * 页面权限码。
     */
    java.util.List<String> pagePermissions;

    /**
     * API 权限码。
     */
    java.util.List<String> apiPermissions;

    /**
     * DATA 授权模板。
     */
    java.util.Map<String, Object> dataGrantTemplate;
}
