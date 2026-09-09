package io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 设置角色授权规则请求。
 *
 * @author surezzzzzz
 */
@Data
public class PutRoleAuthorizationRuleRequest {

    /**
     * 页面权限码列表（必须在应用清单范围内）
     */
    private List<String> pagePermissions;

    /**
     * API权限码列表（必须在应用清单范围内）
     */
    private List<String> apiPermissions;

    /**
     * 数据权限授权模板（DataGrantDocument 形态；null 或缺省 = 无数据授权，
     * grant 必须落在应用清单 DATA 资源声明范围内）
     */
    private Map<String, Object> dataGrantTemplate;
}
