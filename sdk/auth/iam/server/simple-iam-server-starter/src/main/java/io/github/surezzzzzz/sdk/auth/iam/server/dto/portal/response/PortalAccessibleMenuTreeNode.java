package io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Portal 侧边栏可见菜单树节点。
 *
 * <p>PAGE 的 route 已由 IAM 拼接为完整 Portal 路由；children 已按当前用户权限裁剪。
 * presentationMode 仅由这个权限裁剪后的读模型驱动 Portal 宿主布局。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class PortalAccessibleMenuTreeNode {

    private final String code;
    private final String name;
    private final String nodeType;
    private final String icon;
    private final String route;
    private final String requiredPagePermission;
    private final String presentationMode;
    private final Integer sortOrder;
    private final List<PortalAccessibleMenuTreeNode> children;
}
