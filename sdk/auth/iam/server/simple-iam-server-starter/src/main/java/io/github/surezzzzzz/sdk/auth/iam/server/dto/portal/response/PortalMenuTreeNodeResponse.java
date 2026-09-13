package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 可信应用管理面菜单树节点响应。
 *
 * <p>PAGE 的 route 保持相对 routePrefix，不能与 Portal 侧边栏的完整路由读模型混用；
 * presentationMode 始终返回有效值，GROUP 和历史缺失值均为 STANDARD。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class PortalMenuTreeNodeResponse {

    private final String code;
    private final String name;
    private final String nodeType;
    private final String icon;
    private final String route;
    private final String requiredPagePermission;
    private final String presentationMode;
    private final Integer sortOrder;
    private final List<PortalMenuTreeNodeResponse> children;
}
