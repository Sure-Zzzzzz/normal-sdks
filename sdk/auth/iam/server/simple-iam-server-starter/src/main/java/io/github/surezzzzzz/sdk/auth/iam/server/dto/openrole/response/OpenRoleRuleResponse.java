package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 固定应用规则与同事务读取的角色修改版本。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class OpenRoleRuleResponse {
    /**
     * 公开角色 UUID。
     */
    private final String openRoleId;
    /**
     * 固定应用。
     */
    private final Long applicationId;
    /**
     * 角色修改版本。
     */
    private final long revision;
    /**
     * 页面权限。
     */
    private final List<String> pagePermissions;
    /**
     * 接口权限。
     */
    private final List<String> apiPermissions;
    /**
     * 数据授权模板。
     */
    private final Map<String, Object> dataGrantTemplate;
}
