package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 角色应用授权规则响应。
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class RoleAuthorizationRuleResponse {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 可信应用ID
     */
    private Long applicationId;

    /**
     * 页面权限码列表
     */
    private List<String> pagePermissions;

    /**
     * API权限码列表
     */
    private List<String> apiPermissions;

    /**
     * 数据权限授权模板（null 表示无数据授权）
     */
    private Map<String, Object> dataGrantTemplate;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
