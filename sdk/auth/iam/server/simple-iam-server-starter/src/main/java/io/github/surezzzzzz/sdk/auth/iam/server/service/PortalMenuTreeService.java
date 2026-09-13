package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.*;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalMenuTreeNodeRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.MenuItemResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleMenuTreeNode;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMenuItem;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMenuTreeNodeResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationMenuEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationMenuRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Portal 菜单树的校验、持久化和读模型组装服务。
 *
 * <p>所有菜单树写入先完整校验，再替换同一应用的旧快照；GROUP 不能导航，PAGE 不能有子节点。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
@Slf4j
public class PortalMenuTreeService {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_ROUTE_LENGTH = 255;
    private static final int MAX_ROUTE_DECODE_TIMES = 3;

    private final IamTrustedApplicationMenuRepository menuRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;

    /**
     * 应用 Portal 配置中的菜单更新入口。
     *
     * <p>1.0 客户端只认识 {@code menus}。层级菜单上线后，允许旧客户端原样回显该兼容字段，
     * 但不允许它将已有树覆盖成扁平列表。
     */
    public void updateMenus(Long applicationId, List<MenuItemRequest> menus,
                            List<PortalMenuTreeNodeRequest> menuTree) {
        if (menus != null && menuTree != null) {
            throw invalid("menus 与 menuTree 不能同时提交");
        }
        if (menuTree != null) {
            replaceTree(applicationId, menuTree);
            return;
        }
        if (menus == null) {
            return;
        }

        List<IamTrustedApplicationMenuEntity> current = listEntities(applicationId);
        if (containsGroup(current)) {
            if (!isLegacyEcho(current, menus)) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_MENU_TREE_LEGACY_CONFLICT,
                        ServerErrorMessage.TRUSTED_APPLICATION_MENU_TREE_LEGACY_CONFLICT);
            }
            return;
        }
        replaceTree(applicationId, toRootPages(menus));
    }

    /**
     * 管理面读取相对路由的菜单树。
     */
    public List<PortalMenuTreeNodeResponse> getManagementTree(Long applicationId) {
        return buildManagementTree(listEntities(applicationId));
    }

    /**
     * 供 Portal 批量读取路径复用，避免每个应用一次菜单查询。
     */
    public List<PortalMenuTreeNodeResponse> buildManagementTree(List<IamTrustedApplicationMenuEntity> entities) {
        return buildManagement(nodesByParent(entities), null, new HashSet<Long>());
    }

    /**
     * 按页面权限裁剪并返回完整路由树。
     */
    public List<PortalAccessibleMenuTreeNode> buildAccessibleTree(List<IamTrustedApplicationMenuEntity> entities,
                                                                  String routePrefix, Set<String> pagePermissions) {
        Set<String> permissions = pagePermissions == null
                ? Collections.<String>emptySet() : pagePermissions;
        return buildAccessible(nodesByParent(entities), null, new HashSet<Long>(), routePrefix, permissions);
    }

    /**
     * 管理面兼容字段：按树显示顺序展平所有 PAGE。
     */
    public List<MenuItemResponse> flattenManagement(List<PortalMenuTreeNodeResponse> tree) {
        List<MenuItemResponse> result = new ArrayList<>();
        flattenManagement(tree, result);
        return result;
    }

    /**
     * Portal 兼容字段：按树显示顺序展平已裁剪 PAGE。
     */
    public List<PortalMenuItem> flattenAccessible(List<PortalAccessibleMenuTreeNode> tree) {
        List<PortalMenuItem> result = new ArrayList<>();
        flattenAccessible(tree, result);
        return result;
    }

    /**
     * 解析默认入口引用的 PAGE，并规范化其静态子路由。
     *
     * <p>菜单快照保存会重建数据库行，默认入口只能以应用内唯一 code 重找 PAGE，不能引用菜单行 ID。
     */
    public String resolveDefaultEntryPath(Long applicationId, String pageMenuCode, String entryPath) {
        String code = required(pageMenuCode, "默认入口 PAGE 编码", 64);
        IamTrustedApplicationMenuEntity page = listEntities(applicationId).stream()
                .filter(item -> code.equals(item.getCode()))
                .findFirst()
                .orElseThrow(() -> invalidDefaultEntry("默认 PAGE 不存在：" + code));
        if (page.getNodeType() != PortalMenuNodeType.PAGE) {
            throw invalidDefaultEntry("默认入口只能引用 PAGE：" + code);
        }
        String pageRoute = page.getRoute();
        String candidate = text(entryPath);
        if (candidate == null) {
            return pageRoute;
        }
        String decoded = decodeRouteForValidation(candidate);
        if (candidate.length() > MAX_ROUTE_LENGTH || !candidate.startsWith("/")
                || containsUnsafeDefaultEntryContent(candidate) || containsUnsafeDefaultEntryContent(decoded)) {
            throw invalidDefaultEntry("入口路径必须是应用内静态路径");
        }
        String normalized = candidate.replaceAll("/{2,}", "/");
        if (!"/".equals(pageRoute) && !normalized.equals(pageRoute)
                && !normalized.startsWith(pageRoute + "/")) {
            throw invalidDefaultEntry("入口路径必须属于默认 PAGE 路由：" + pageRoute);
        }
        return normalized;
    }

    private List<PortalMenuTreeNodeRequest> toRootPages(List<MenuItemRequest> menus) {
        List<PortalMenuTreeNodeRequest> roots = new ArrayList<>();
        for (MenuItemRequest menu : menus) {
            PortalMenuTreeNodeRequest node = new PortalMenuTreeNodeRequest();
            node.setCode(menu.getCode());
            node.setName(menu.getName());
            node.setNodeType(PortalMenuNodeType.PAGE.name());
            // 1.0 menus 使用不带前导斜杠的应用内相对路由；入树前统一成 1.1 规范。
            node.setRoute(normalizeLegacyRoute(menu.getRoute()));
            node.setSortOrder(menu.getSortOrder());
            node.setChildren(Collections.<PortalMenuTreeNodeRequest>emptyList());
            roots.add(node);
        }
        return roots;
    }

    private void replaceTree(Long applicationId, List<PortalMenuTreeNodeRequest> roots) {
        Set<String> allowedPermissions = declaredPagePermissions(applicationId);
        List<WriteNode> validated = validate(roots, 1, new HashSet<String>(), allowedPermissions);
        log.debug("Portal 菜单树写入校验完成：applicationId={}, rootCount={}", applicationId, validated.size());
        menuRepository.deleteByApplicationId(applicationId);
        for (WriteNode node : validated) {
            save(applicationId, null, node);
        }
    }

    private List<WriteNode> validate(List<PortalMenuTreeNodeRequest> nodes, int depth, Set<String> codes,
                                     Set<String> allowedPermissions) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        if (depth > MAX_DEPTH) {
            throw invalid("菜单层级不能超过 " + MAX_DEPTH);
        }

        List<WriteNode> result = new ArrayList<>();
        int defaultSortOrder = 0;
        for (PortalMenuTreeNodeRequest node : nodes) {
            if (node == null) {
                throw invalid("菜单节点不能为空");
            }
            String code = required(node.getCode(), "菜单编码", 64);
            if (!codes.add(code)) {
                throw invalid("菜单编码重复：" + code);
            }
            String name = required(node.getName(), "菜单名称", 128);
            PortalMenuNodeType nodeType = parseNodeType(node.getNodeType());
            List<PortalMenuTreeNodeRequest> children = safeChildren(node.getChildren());
            String route = text(node.getRoute());
            String requiredPagePermission = text(node.getRequiredPagePermission());
            String icon = validIcon(text(node.getIcon()));
            PortalPresentationMode presentationMode;

            if (nodeType == PortalMenuNodeType.GROUP) {
                validateGroup(route, requiredPagePermission, text(node.getPresentationMode()), children);
                presentationMode = PortalPresentationMode.STANDARD;
            } else {
                validatePage(children);
                route = validRoute(route);
                validatePagePermission(requiredPagePermission, allowedPermissions);
                presentationMode = parsePresentationMode(node.getPresentationMode());
            }

            WriteNode item = new WriteNode(code, name, nodeType, icon, route, requiredPagePermission, presentationMode,
                    node.getSortOrder() == null ? defaultSortOrder : node.getSortOrder());
            item.children = validate(children, depth + 1, codes, allowedPermissions);
            result.add(item);
            defaultSortOrder++;
        }
        return result;
    }

    private PortalMenuNodeType parseNodeType(String value) {
        try {
            return PortalMenuNodeType.valueOf(required(value, "节点类型", 16));
        } catch (IllegalArgumentException exception) {
            throw invalid("节点类型仅支持 GROUP 或 PAGE");
        }
    }

    private List<PortalMenuTreeNodeRequest> safeChildren(List<PortalMenuTreeNodeRequest> children) {
        return children == null ? Collections.<PortalMenuTreeNodeRequest>emptyList() : children;
    }

    private void validateGroup(String route, String requiredPagePermission, String presentationMode,
                               List<PortalMenuTreeNodeRequest> children) {
        if (route != null || requiredPagePermission != null || presentationMode != null || children.isEmpty()) {
            throw invalid("GROUP 只能包含非空子节点");
        }
    }

    private PortalPresentationMode parsePresentationMode(String value) {
        if (value == null) {
            return PortalPresentationMode.STANDARD;
        }
        try {
            return PortalPresentationMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw invalid("PAGE 展示模式仅支持 STANDARD 或 IMMERSIVE");
        }
    }

    private void validatePage(List<PortalMenuTreeNodeRequest> children) {
        if (!children.isEmpty()) {
            throw invalid("PAGE 不能包含子节点");
        }
    }

    private void validatePagePermission(String requiredPagePermission, Set<String> allowedPermissions) {
        if (requiredPagePermission != null && !allowedPermissions.contains(requiredPagePermission)) {
            throw invalid("页面权限未在应用清单中声明：" + requiredPagePermission);
        }
    }

    private void save(Long applicationId, Long parentId, WriteNode node) {
        IamTrustedApplicationMenuEntity entity = new IamTrustedApplicationMenuEntity();
        entity.setApplicationId(applicationId);
        entity.setParentId(parentId);
        entity.setCode(node.code);
        entity.setName(node.name);
        entity.setNodeType(node.type);
        entity.setIcon(node.icon);
        entity.setRoute(node.route);
        entity.setRequiredPagePermission(node.permission);
        entity.setPresentationMode(node.presentationMode);
        entity.setSortOrder(node.sortOrder);
        entity = menuRepository.save(entity);
        for (WriteNode child : node.children) {
            save(applicationId, entity.getId(), child);
        }
    }

    private Set<String> declaredPagePermissions(Long applicationId) {
        IamApplicationPermissionManifestEntity manifest = manifestRepository.findByApplicationId(applicationId)
                .orElse(null);
        if (manifest == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(IamApplicationAuthorizationJsonCodec
                .readStringList(manifest.getPagePermissionsJson(), "pagePermissions"));
    }

    private List<IamTrustedApplicationMenuEntity> listEntities(Long applicationId) {
        return menuRepository.findByApplicationIdOrderBySortOrderAsc(applicationId);
    }

    private boolean containsGroup(List<IamTrustedApplicationMenuEntity> entities) {
        for (IamTrustedApplicationMenuEntity entity : entities) {
            if (entity.getNodeType() == PortalMenuNodeType.GROUP) {
                return true;
            }
        }
        return false;
    }

    private boolean isLegacyEcho(List<IamTrustedApplicationMenuEntity> entities, List<MenuItemRequest> menus) {
        List<MenuItemResponse> expected = flattenManagement(buildManagementTree(entities));
        if (expected.size() != menus.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            MenuItemResponse expectedMenu = expected.get(index);
            MenuItemRequest submittedMenu = menus.get(index);
            if (!isSameLegacyMenu(expectedMenu, submittedMenu)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameLegacyMenu(MenuItemResponse expected, MenuItemRequest submitted) {
        return submitted != null
                && expected.getCode().equals(text(submitted.getCode()))
                && expected.getName().equals(text(submitted.getName()))
                && expected.getRoute().equals(text(submitted.getRoute()))
                && Objects.equals(expected.getSortOrder(), submitted.getSortOrder());
    }

    private Map<Long, List<IamTrustedApplicationMenuEntity>> nodesByParent(
            List<IamTrustedApplicationMenuEntity> entities) {
        Map<Long, List<IamTrustedApplicationMenuEntity>> result = new HashMap<>();
        for (IamTrustedApplicationMenuEntity entity : entities) {
            result.computeIfAbsent(entity.getParentId(), key -> new ArrayList<>()).add(entity);
        }
        for (List<IamTrustedApplicationMenuEntity> children : result.values()) {
            children.sort(Comparator.comparing(IamTrustedApplicationMenuEntity::getSortOrder)
                    .thenComparing(IamTrustedApplicationMenuEntity::getId));
        }
        return result;
    }

    private List<PortalMenuTreeNodeResponse> buildManagement(
            Map<Long, List<IamTrustedApplicationMenuEntity>> byParent, Long parentId, Set<Long> path) {
        List<PortalMenuTreeNodeResponse> result = new ArrayList<>();
        for (IamTrustedApplicationMenuEntity entity : childrenOf(byParent, parentId)) {
            if (!path.add(entity.getId())) {
                continue;
            }
            List<PortalMenuTreeNodeResponse> children = buildManagement(byParent, entity.getId(), new HashSet<>(path));
            result.add(PortalMenuTreeNodeResponse.builder()
                    .code(entity.getCode())
                    .name(entity.getName())
                    .nodeType(entity.getNodeType().name())
                    .icon(entity.getIcon())
                    .route(entity.getRoute())
                    .requiredPagePermission(entity.getRequiredPagePermission())
                    .presentationMode(presentationModeOf(entity).name())
                    .sortOrder(entity.getSortOrder())
                    .children(children)
                    .build());
        }
        return result;
    }

    private List<PortalAccessibleMenuTreeNode> buildAccessible(
            Map<Long, List<IamTrustedApplicationMenuEntity>> byParent, Long parentId, Set<Long> path,
            String routePrefix, Set<String> pagePermissions) {
        List<PortalAccessibleMenuTreeNode> result = new ArrayList<>();
        for (IamTrustedApplicationMenuEntity entity : childrenOf(byParent, parentId)) {
            if (!path.add(entity.getId())) {
                continue;
            }
            List<PortalAccessibleMenuTreeNode> children = buildAccessible(byParent, entity.getId(), new HashSet<>(path),
                    routePrefix, pagePermissions);
            if (entity.getNodeType() == PortalMenuNodeType.GROUP && children.isEmpty()) {
                continue;
            }
            if (entity.getNodeType() == PortalMenuNodeType.PAGE
                    && entity.getRequiredPagePermission() != null
                    && !pagePermissions.contains(entity.getRequiredPagePermission())) {
                continue;
            }
            result.add(PortalAccessibleMenuTreeNode.builder()
                    .code(entity.getCode())
                    .name(entity.getName())
                    .nodeType(entity.getNodeType().name())
                    .icon(entity.getIcon())
                    .route(entity.getNodeType() == PortalMenuNodeType.PAGE ? routePrefix + entity.getRoute() : null)
                    .requiredPagePermission(entity.getRequiredPagePermission())
                    .presentationMode(presentationModeOf(entity).name())
                    .sortOrder(entity.getSortOrder())
                    .children(children)
                    .build());
        }
        return result;
    }

    private List<IamTrustedApplicationMenuEntity> childrenOf(
            Map<Long, List<IamTrustedApplicationMenuEntity>> byParent, Long parentId) {
        return byParent.getOrDefault(parentId, Collections.<IamTrustedApplicationMenuEntity>emptyList());
    }

    /**
     * 兼容历史手工数据：展示读模型缺失模式时按历史布局处理，不能因空值中断 Portal。
     */
    private PortalPresentationMode presentationModeOf(IamTrustedApplicationMenuEntity entity) {
        return entity.getPresentationMode() == null ? PortalPresentationMode.STANDARD : entity.getPresentationMode();
    }

    private void flattenManagement(List<PortalMenuTreeNodeResponse> nodes, List<MenuItemResponse> target) {
        for (PortalMenuTreeNodeResponse node : nodes) {
            if (PortalMenuNodeType.PAGE.name().equals(node.getNodeType())) {
                target.add(MenuItemResponse.builder()
                        .code(node.getCode())
                        .name(node.getName())
                        .route(node.getRoute())
                        .sortOrder(node.getSortOrder())
                        .build());
            }
            flattenManagement(node.getChildren(), target);
        }
    }

    private void flattenAccessible(List<PortalAccessibleMenuTreeNode> nodes, List<PortalMenuItem> target) {
        for (PortalAccessibleMenuTreeNode node : nodes) {
            if (PortalMenuNodeType.PAGE.name().equals(node.getNodeType())) {
                target.add(PortalMenuItem.builder()
                        .code(node.getCode())
                        .name(node.getName())
                        .route(node.getRoute())
                        .sortOrder(node.getSortOrder())
                        .build());
            }
            flattenAccessible(node.getChildren(), target);
        }
    }

    private String validRoute(String route) {
        String decoded = decodeRouteForValidation(route);
        if (route == null || route.length() > MAX_ROUTE_LENGTH || !route.startsWith("/") || route.startsWith("//")
                || containsUnsafeRouteContent(route) || containsUnsafeRouteContent(decoded)) {
            throw invalid("PAGE 路由必须是应用内相对路径");
        }
        return route;
    }

    /**
     * 仅用于拒绝危险输入，不将解码结果回写，避免调用方误以为服务端会修复路由。
     */
    private String decodeRouteForValidation(String route) {
        if (route == null) {
            return null;
        }
        String decoded = route;
        try {
            for (int index = 0; index < MAX_ROUTE_DECODE_TIMES && decoded.contains("%"); index++) {
                String next = URLDecoder.decode(decoded, StandardCharsets.UTF_8.name());
                if (next.equals(decoded)) {
                    break;
                }
                decoded = next;
            }
            return decoded;
        } catch (IllegalArgumentException | UnsupportedEncodingException exception) {
            throw invalid("PAGE 路由编码非法");
        }
    }

    private boolean containsUnsafeRouteContent(String route) {
        return route == null || route.startsWith("//") || route.contains("://") || route.contains("..")
                || route.contains("?") || route.contains("#") || route.contains("\\");
    }

    private boolean containsUnsafeDefaultEntryContent(String path) {
        if (containsUnsafeRouteContent(path) || path.contains("\u0000")) {
            return true;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private String validIcon(String icon) {
        if (icon != null && !TrustedApplicationIcon.isSupported(icon)) {
            throw invalid("菜单图标不受支持：" + icon);
        }
        return icon;
    }

    private String normalizeLegacyRoute(String route) {
        String normalized = text(route);
        return normalized != null && !normalized.startsWith("/") ? "/" + normalized : normalized;
    }

    private String required(String value, String field, int maxLength) {
        String normalized = text(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw invalid(field + "不能为空且长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private SimpleIamServerException invalid(String detail) {
        return new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_MENU_TREE_INVALID,
                String.format(ServerErrorMessage.TRUSTED_APPLICATION_MENU_TREE_INVALID, detail));
    }

    private SimpleIamServerException invalidDefaultEntry(String detail) {
        return new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID,
                String.format(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID, detail));
    }

    private static final class WriteNode {

        private final String code;
        private final String name;
        private final PortalMenuNodeType type;
        private final String icon;
        private final String route;
        private final String permission;
        private final PortalPresentationMode presentationMode;
        private final Integer sortOrder;
        private List<WriteNode> children = Collections.emptyList();

        private WriteNode(String code, String name, PortalMenuNodeType type, String icon, String route,
                          String permission, PortalPresentationMode presentationMode, Integer sortOrder) {
            this.code = code;
            this.name = name;
            this.type = type;
            this.icon = icon;
            this.route = route;
            this.permission = permission;
            this.presentationMode = presentationMode;
            this.sortOrder = sortOrder;
        }
    }
}
