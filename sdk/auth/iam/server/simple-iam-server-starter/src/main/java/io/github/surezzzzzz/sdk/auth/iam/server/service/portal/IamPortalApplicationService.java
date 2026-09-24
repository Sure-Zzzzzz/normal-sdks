package io.github.surezzzzzz.sdk.auth.iam.server.service.portal;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleMenuTreeNode;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalDefaultEntryResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalNavigationContextResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamPortalSettingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationMenuEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.portal.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamPortalSettingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationMenuRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamPlatformAdminPrivilegeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Portal 侧边栏应用读模型服务
 *
 * <p>按当前登录用户的应用授权（admitted=1 且授权状态有效）过滤、且启用 Portal 集成的
 * 应用组装侧边栏数据。只读，与应用 CRUD 解耦；授权管理面变更立即生效（下一次拉取即体现）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPortalApplicationService {

    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamTrustedApplicationPortalRepository trustedApplicationPortalRepository;
    private final IamPortalSettingRepository portalSettingRepository;
    private final IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamPlatformAdminPrivilegeService platformAdminPrivilegeSupport;
    private final IamPortalMenuTreeService portalMenuTreeService;

    /**
     * 侧边栏：当前用户被授权（admitted=1 且状态有效）、启用 Portal 集成的应用 + 菜单；
     * 平台管理员（挂内置 iam_admin）特权全量可达，不按授权行过滤。
     *
     * @param userId 当前登录用户ID
     * @return 可访问应用列表（菜单 route 已拼成完整路径）
     */
    public List<PortalAccessibleApplication> listPortalAccessibleApplications(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        boolean platformAdmin = platformAdminPrivilegeSupport.isPlatformAdmin(userId);
        Map<Long, IamApplicationAuthorizationEntity> authorizations = applicationAuthorizationRepository
                .findByUserId(userId).stream().filter(this::isActiveAndAdmitted)
                .collect(Collectors.toMap(IamApplicationAuthorizationEntity::getApplicationId, item -> item));
        List<IamTrustedApplicationPortalEntity> portals = trustedApplicationPortalRepository
                .findByEnabledOrderBySortOrderAscApplicationIdAsc(SimpleIamServerConstant.STATUS_ACTIVE);
        List<Long> applicationIds = portals.stream().map(IamTrustedApplicationPortalEntity::getApplicationId)
                .collect(Collectors.toList());
        Map<Long, IamTrustedApplicationEntity> applications = new HashMap<>();
        for (IamTrustedApplicationEntity application : trustedApplicationRepository.findAllById(applicationIds))
            applications.put(application.getId(), application);
        Map<Long, List<IamTrustedApplicationMenuEntity>> menus = trustedApplicationMenuRepository.findByApplicationIdIn(applicationIds)
                .stream().collect(Collectors.groupingBy(IamTrustedApplicationMenuEntity::getApplicationId));
        Map<Long, IamApplicationPermissionManifestEntity> manifests = platformAdmin
                ? manifestRepository.findByApplicationIdIn(applicationIds).stream().collect(Collectors.toMap(
                IamApplicationPermissionManifestEntity::getApplicationId, item -> item))
                : Collections.<Long, IamApplicationPermissionManifestEntity>emptyMap();
        List<PortalAccessibleApplication> result = new ArrayList<>();
        for (IamTrustedApplicationPortalEntity portal : portals) {
            IamApplicationAuthorizationEntity authorization = authorizations.get(portal.getApplicationId());
            if (!platformAdmin && authorization == null) {
                continue;
            }
            IamTrustedApplicationEntity app = applications.get(portal.getApplicationId());
            if (app == null || app.getStatus() == null
                    || SimpleIamServerConstant.STATUS_ACTIVE != app.getStatus().intValue()) {
                continue;
            }
            Set<String> pagePermissions = resolvePagePermissions(portal.getApplicationId(), authorization,
                    manifests.get(portal.getApplicationId()), platformAdmin);
            List<IamTrustedApplicationMenuEntity> applicationMenus = menus.getOrDefault(portal.getApplicationId(), Collections.emptyList());
            List<PortalAccessibleMenuTreeNode> tree = portalMenuTreeService.buildAccessibleTree(applicationMenus,
                    portal.getRoutePrefix(), pagePermissions);
            if (!applicationMenus.isEmpty() && tree.isEmpty()) continue;
            result.add(toAccessible(app, portal, tree));
        }
        return result;
    }

    /**
     * Portal 根路由和登录回跳使用的导航上下文。
     *
     * <p>全局首页和应用默认入口都只从已按当前用户权限裁剪的应用集合中选择。
     */
    public PortalNavigationContextResponse getNavigationContext(Long userId) {
        List<PortalAccessibleApplication> applications = listPortalAccessibleApplications(userId);
        IamPortalSettingEntity setting = portalSettingRepository.findById(IamPortalSettingEntity.SINGLETON_ID)
                .orElse(null);
        String loginLandingApplicationCode = null;
        if (setting != null && setting.getLoginLandingApplicationId() != null) {
            String configuredCode = trustedApplicationRepository.findById(setting.getLoginLandingApplicationId())
                    .map(IamTrustedApplicationEntity::getApplicationCode).orElse(null);
            if (configuredCode != null) {
                for (PortalAccessibleApplication application : applications) {
                    if (configuredCode.equals(application.getApplicationCode())
                            && application.getDefaultEntry() != null) {
                        loginLandingApplicationCode = configuredCode;
                        break;
                    }
                }
            }
        }
        return PortalNavigationContextResponse.builder()
                .applications(applications)
                .loginLandingApplicationCode(loginLandingApplicationCode)
                .build();
    }

    private boolean isActiveAndAdmitted(IamApplicationAuthorizationEntity authorization) {
        return authorization.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getStatus().intValue()
                && authorization.getAdmitted() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getAdmitted().intValue();
    }

    private Set<String> resolvePagePermissions(Long applicationId, IamApplicationAuthorizationEntity authorization,
                                               IamApplicationPermissionManifestEntity manifest, boolean platformAdmin) {
        try {
            if (!platformAdmin) return authorization == null ? Collections.emptySet() : new HashSet<>(
                    IamApplicationAuthorizationJsonCodec.readStringList(authorization.getPagePermissionsJson(), "pagePermissions"));
            return manifest == null ? Collections.emptySet() : new HashSet<>(
                    IamApplicationAuthorizationJsonCodec.readStringList(manifest.getPagePermissionsJson(), "pagePermissions"));
        } catch (RuntimeException exception) {
            log.debug("Portal 菜单页面权限解析失败：applicationId={}, exceptionType={}", applicationId,
                    exception.getClass().getSimpleName());
            return Collections.emptySet();
        }
    }

    private PortalAccessibleApplication toAccessible(IamTrustedApplicationEntity app,
                                                     IamTrustedApplicationPortalEntity portal,
                                                     List<PortalAccessibleMenuTreeNode> tree) {
        return PortalAccessibleApplication.builder()
                .applicationCode(app.getApplicationCode())
                .applicationName(app.getApplicationName())
                .description(app.getDescription())
                .icon(app.getIcon())
                .routePrefix(portal.getRoutePrefix())
                .entry(portal.getEntry())
                .apiBase(portal.getApiBase())
                .menus(portalMenuTreeService.flattenAccessible(tree))
                .menuTree(tree)
                .defaultEntry(visibleDefaultEntry(portal, tree))
                .build();
    }

    private PortalDefaultEntryResponse visibleDefaultEntry(IamTrustedApplicationPortalEntity portal,
                                                           List<PortalAccessibleMenuTreeNode> tree) {
        if (portal.getDefaultPageMenuCode() == null || portal.getDefaultEntryPath() == null) {
            return null;
        }
        boolean visible = portalMenuTreeService.flattenAccessible(tree).stream()
                .anyMatch(item -> portal.getDefaultPageMenuCode().equals(item.getCode()));
        return visible ? PortalDefaultEntryResponse.builder()
                .pageMenuCode(portal.getDefaultPageMenuCode())
                .path(portal.getDefaultEntryPath())
                .build() : null;
    }
}
