package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalMenuItem;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationMenuEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationPortalEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationMenuRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final IamPlatformAdminPrivilegeSupport platformAdminPrivilegeSupport;

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
        Set<Long> admittedApplicationIds = applicationAuthorizationRepository.findByUserId(userId).stream()
                .filter(authorization -> isActiveAndAdmitted(authorization))
                .map(IamApplicationAuthorizationEntity::getApplicationId)
                .collect(Collectors.toSet());
        List<IamTrustedApplicationPortalEntity> portals = trustedApplicationPortalRepository
                .findByEnabled(SimpleIamServerConstant.STATUS_ACTIVE);
        List<PortalAccessibleApplication> result = new ArrayList<>();
        for (IamTrustedApplicationPortalEntity portal : portals) {
            if (!platformAdmin && !admittedApplicationIds.contains(portal.getApplicationId())) {
                continue;
            }
            IamTrustedApplicationEntity app = trustedApplicationRepository
                    .findById(portal.getApplicationId()).orElse(null);
            if (app == null) {
                continue;
            }
            result.add(toAccessible(app, portal));
        }
        return result;
    }

    private boolean isActiveAndAdmitted(IamApplicationAuthorizationEntity authorization) {
        return authorization.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getStatus().intValue()
                && authorization.getAdmitted() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getAdmitted().intValue();
    }

    private PortalAccessibleApplication toAccessible(IamTrustedApplicationEntity app,
                                                     IamTrustedApplicationPortalEntity portal) {
        List<IamTrustedApplicationMenuEntity> menuEntities = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(portal.getApplicationId());
        List<PortalMenuItem> menus = menuEntities.stream()
                .map(m -> PortalMenuItem.builder()
                        .code(m.getCode())
                        .name(m.getName())
                        .route(buildFullRoute(portal.getRoutePrefix(), m.getRoute()))
                        .sortOrder(m.getSortOrder())
                        .build())
                .collect(Collectors.toList());
        return PortalAccessibleApplication.builder()
                .applicationCode(app.getApplicationCode())
                .applicationName(app.getApplicationName())
                .description(app.getDescription())
                .icon(app.getIcon())
                .routePrefix(portal.getRoutePrefix())
                .entry(portal.getEntry())
                .apiBase(portal.getApiBase())
                .menus(menus)
                .build();
    }

    /**
     * 菜单完整路径 = routePrefix + 相对路径；保证相对路径以 / 开头，避免拼接错位。
     */
    private String buildFullRoute(String routePrefix, String relativeRoute) {
        if (relativeRoute == null || relativeRoute.isEmpty()) {
            return routePrefix;
        }
        String normalized = relativeRoute.startsWith("/") ? relativeRoute : "/" + relativeRoute;
        return routePrefix + normalized;
    }
}
