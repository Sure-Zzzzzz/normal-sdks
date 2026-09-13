package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.*;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalAccessibleApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.response.PortalIntegrationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 门户侧边栏可达列表测试（授权行过滤 + 平台管理员特权直通）
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamPortalApplicationServiceTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "portal-admin-" + suffix;
    private final String userUsername = "portal-user-" + suffix;
    private final String applicationCodeA = "portal-a-" + suffix;
    private final String applicationCodeB = "portal-b-" + suffix;

    @Autowired
    private IamPortalApplicationService portalApplicationService;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @SpyBean
    private IamApplicationPermissionManifestRepository manifestRepository;

    private Long adminUserId;
    private Long targetUserId;
    private Long applicationIdA;
    private Long applicationIdB;

    @BeforeEach
    void prepare() {
        CreateUserRequest adminRequest = new CreateUserRequest();
        adminRequest.setUsername(adminUsername);
        adminRequest.setPassword("Admin@1234");
        adminRequest.setDisplayName(adminUsername);
        adminUserId = userService.createUser(adminRequest).getId();
        roleService.assignRole(adminUserId,
                roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());

        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(userUsername);
        userRequest.setPassword("Admin@1234");
        userRequest.setDisplayName(userUsername);
        targetUserId = userService.createUser(userRequest).getId();

        applicationIdA = createPortalApplication(applicationCodeA);
        applicationIdB = createPortalApplication(applicationCodeB);
        grantAdmitted(targetUserId, applicationIdA);
    }

    @AfterEach
    void cleanup() {
        authorizationRepository.findByUserId(adminUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        authorizationRepository.findByUserId(targetUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        if (applicationIdA != null) {
            trustedApplicationService.deleteApplication(applicationIdA);
        }
        if (applicationIdB != null) {
            trustedApplicationService.deleteApplication(applicationIdB);
        }
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("平台管理员无授权行特权直通全部启用应用，普通用户按授权行过滤")
    void testPortalFilteringAndPlatformAdminPassThrough() {
        List<String> adminCodes = accessibleCodes(adminUserId);
        assertTrue(adminCodes.contains(applicationCodeA), "平台管理员应直通应用 A（无授权行）");
        assertTrue(adminCodes.contains(applicationCodeB), "平台管理员应直通应用 B（无授权行）");

        List<String> userCodes = accessibleCodes(targetUserId);
        assertTrue(userCodes.contains(applicationCodeA), "普通用户应看到已授权应用 A");
        assertFalse(userCodes.contains(applicationCodeB), "普通用户不得看到未授权应用 B（特权不外溢）");

        log.info("门户可达列表断言完成：adminCodes={}, userCodes={}", adminCodes, userCodes);
    }

    @Test
    @DisplayName("平台管理员多个应用的权限清单应一次批量读取，不得退化为逐应用查询")
    void platformAdminReadsManifestsInBatch() {
        clearInvocations(manifestRepository);

        List<String> adminCodes = accessibleCodes(adminUserId);

        assertTrue(adminCodes.contains(applicationCodeA), "平台管理员批量投影后仍应看到应用 A");
        assertTrue(adminCodes.contains(applicationCodeB), "平台管理员批量投影后仍应看到应用 B");
        verify(manifestRepository, times(1)).findByApplicationIdIn(anyList());
        verify(manifestRepository, never()).findByApplicationId(anyLong());
        log.info("平台管理员权限清单批量读取断言完成：adminCodes={}", adminCodes);
    }

    @Test
    @DisplayName("摘除 iam_admin 后特权直通立即失效，退回授权行口径")
    void testPortalPrivilegeRevokedWithRole() {
        roleService.revokeRole(adminUserId,
                roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());

        List<String> adminCodes = accessibleCodes(adminUserId);
        assertFalse(adminCodes.contains(applicationCodeA), "摘角色后无授权行的应用 A 应不可见");
        assertFalse(adminCodes.contains(applicationCodeB), "摘角色后无授权行的应用 B 应不可见");

        log.info("摘除 iam_admin 门户直通失效断言完成：adminCodes={}", adminCodes);
    }

    @Test
    @DisplayName("普通用户读取递归菜单树时保留树结构并兼容扁平 menus")
    void testPortalMenuTreeAndLegacyMenusCompatibility() {
        PortalMenuTreeNodeRequest page = new PortalMenuTreeNodeRequest();
        page.setCode("member-list");
        page.setName("成员列表");
        page.setNodeType("PAGE");
        page.setIcon("users");
        page.setRoute("/members");
        page.setPresentationMode("IMMERSIVE");
        page.setSortOrder(1);
        page.setChildren(Collections.emptyList());

        PortalMenuTreeNodeRequest group = new PortalMenuTreeNodeRequest();
        group.setCode("organization");
        group.setName("组织管理");
        group.setNodeType("GROUP");
        group.setIcon("folder");
        group.setSortOrder(1);
        group.setChildren(Collections.singletonList(page));

        PortalIntegrationRequest portal = new PortalIntegrationRequest();
        portal.setEnabled(true);
        portal.setEntry("//localhost:9xxx/" + applicationCodeA + "/");
        portal.setApiBase("/" + applicationCodeA);
        portal.setMenuTree(Collections.singletonList(group));

        UpdateTrustedApplicationRequest update = new UpdateTrustedApplicationRequest();
        update.setPortal(portal);
        trustedApplicationService.updateApplication(applicationIdA, update);

        PortalAccessibleApplication application = portalApplicationService
                .listPortalAccessibleApplications(targetUserId).stream()
                .filter(item -> applicationCodeA.equals(item.getApplicationCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("已准入应用必须返回"));

        assertEquals(1, application.getMenuTree().size(), "新 Portal 应收到一个根分组");
        assertEquals("GROUP", application.getMenuTree().get(0).getNodeType());
        assertEquals("folder", application.getMenuTree().get(0).getIcon());
        assertEquals("member-list", application.getMenuTree().get(0).getChildren().get(0).getCode());
        assertEquals("users", application.getMenuTree().get(0).getChildren().get(0).getIcon());
        assertEquals("/app/" + applicationCodeA + "/members",
                application.getMenuTree().get(0).getChildren().get(0).getRoute());
        assertEquals("STANDARD", application.getMenuTree().get(0).getPresentationMode(),
                "GROUP 响应必须稳定返回标准展示模式");
        assertEquals("IMMERSIVE", application.getMenuTree().get(0).getChildren().get(0).getPresentationMode(),
                "Portal 只能从权限裁剪后的 PAGE 读模型取得沉浸展示模式");
        assertEquals(1, application.getMenus().size(), "旧 Portal 只接收可导航叶子");
        assertEquals("member-list", application.getMenus().get(0).getCode());
    }

    @Test
    @DisplayName("旧 menus 原样回显不得压平层级树，变更内容应被拒绝")
    void legacyMenusEchoPreservesTreeAndRejectsOverwrite() {
        updateApplicationMenuTree(applicationIdA, Collections.singletonList(groupWithPage("organization", "组织管理",
                page("member-list", "成员列表", "/members", null))));

        PortalIntegrationRequest legacyPortal = enabledPortal(applicationCodeA);
        legacyPortal.setMenus(Collections.singletonList(legacyMenu("member-list", "成员列表", "/members")));
        trustedApplicationService.updateApplication(applicationIdA, updateRequest(legacyPortal));

        assertEquals("GROUP", trustedApplicationService.getApplication(applicationIdA).getPortal()
                .getMenuTree().get(0).getNodeType(), "旧客户端原样保存不能丢失分组层级");

        legacyPortal.setMenus(Collections.singletonList(legacyMenu("member-list", "已篡改名称", "/members")));
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationService.updateApplication(applicationIdA, updateRequest(legacyPortal)));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_MENU_TREE_LEGACY_CONFLICT, exception.getErrorCode());
        assertEquals("组织管理", trustedApplicationService.getApplication(applicationIdA).getPortal()
                .getMenuTree().get(0).getName(), "拒绝覆盖后原树必须保持不变");

        log.info("旧 menus 兼容保护断言完成：applicationId={}", applicationIdA);
    }

    @Test
    @DisplayName("菜单树结构、路由、深度、编码与双来源组合均应在服务端拒绝")
    void menuTreeValidationRejectsInvalidInput() {
        PortalMenuTreeNodeRequest validPage = page("valid-page", "有效页面", "/valid", null);

        PortalMenuTreeNodeRequest groupWithRoute = groupWithPage("group-route", "非法分组", validPage);
        groupWithRoute.setRoute("/not-allowed");
        assertMenuTreeInvalid(Collections.singletonList(groupWithRoute));

        PortalMenuTreeNodeRequest groupWithPresentationMode = groupWithPage("group-presentation", "非法分组", validPage);
        groupWithPresentationMode.setPresentationMode("STANDARD");
        assertMenuTreeInvalid(Collections.singletonList(groupWithPresentationMode));

        PortalMenuTreeNodeRequest pageWithUnknownPresentationMode = page("page-presentation", "非法展示模式", "/presentation", null);
        pageWithUnknownPresentationMode.setPresentationMode("FULLSCREEN");
        assertMenuTreeInvalid(Collections.singletonList(pageWithUnknownPresentationMode));

        PortalMenuTreeNodeRequest pageWithChild = page("page-parent", "非法页面", "/page", null);
        pageWithChild.setChildren(Collections.singletonList(page("page-child", "子页面", "/child", null)));
        assertMenuTreeInvalid(Collections.singletonList(pageWithChild));

        PortalMenuTreeNodeRequest pageWithUnsupportedIcon = page("page-icon", "非法图标", "/page-icon", null);
        pageWithUnsupportedIcon.setIcon("external-image");
        assertMenuTreeInvalid(Collections.singletonList(pageWithUnsupportedIcon));

        PortalMenuTreeNodeRequest fifthLevelPage = page("level-5", "第五层页面", "/level-5", null);
        PortalMenuTreeNodeRequest level4 = groupWithPage("level-4", "第四层分组", fifthLevelPage);
        PortalMenuTreeNodeRequest level3 = groupWithPage("level-3", "第三层分组", level4);
        PortalMenuTreeNodeRequest level2 = groupWithPage("level-2", "第二层分组", level3);
        PortalMenuTreeNodeRequest level1 = groupWithPage("level-1", "第一层分组", level2);
        assertMenuTreeInvalid(Collections.singletonList(level1));

        assertMenuTreeInvalid(java.util.Arrays.asList(
                page("duplicate", "重复页面一", "/one", null),
                page("duplicate", "重复页面二", "/two", null)));
        assertMenuTreeInvalid(Collections.singletonList(page("external", "外部路由", "https://example.test/page", null)));
        assertMenuTreeInvalid(Collections.singletonList(page("encoded-parent", "编码越界", "/%2e%2e/private", null)));
        assertMenuTreeInvalid(Collections.singletonList(page("encoded-parent-upper", "编码越界", "/%2E%2E/private", null)));
        assertMenuTreeInvalid(Collections.singletonList(page("encoded-backslash", "编码反斜杠", "/safe%5cprivate", null)));
        assertMenuTreeInvalid(Collections.singletonList(page("long-route", "超长路由", longRoute(), null)));

        PortalIntegrationRequest bothSources = enabledPortal(applicationCodeA);
        bothSources.setMenus(Collections.singletonList(legacyMenu("legacy", "旧菜单", "/legacy")));
        bothSources.setMenuTree(Collections.singletonList(page("tree", "新菜单", "/tree", null)));
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationService.updateApplication(applicationIdA, updateRequest(bothSources)));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_MENU_TREE_INVALID, exception.getErrorCode());

        log.info("菜单树服务端负向校验完成：applicationId={}", applicationIdA);
    }

    @Test
    @DisplayName("页面权限应裁剪普通用户菜单，平台管理员全量可见且清单不能留下悬挂引用")
    void pagePermissionPruningAndManifestReferenceProtection() {
        String pagePermission = "organization:member:read";
        putManifest(applicationIdA, Collections.singletonList(pagePermission));
        updateApplicationMenuTree(applicationIdA, Collections.singletonList(groupWithPage("organization", "组织管理",
                page("member-list", "成员列表", "/members", pagePermission))));

        assertFalse(accessibleCodes(targetUserId).contains(applicationCodeA),
                "普通用户未获得页面权限时，空分组应一起裁剪且应用不可见");

        PortalAccessibleApplication adminApplication = findAccessibleApplication(adminUserId, applicationCodeA);
        assertEquals("organization", adminApplication.getMenuTree().get(0).getCode(),
                "平台管理员应以权限清单获得完整菜单树");

        authorizationRepository.findByUserId(targetUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        grantAdmitted(targetUserId, applicationIdA, Collections.singletonList(pagePermission));
        PortalAccessibleApplication userApplication = findAccessibleApplication(targetUserId, applicationCodeA);
        assertEquals("member-list", userApplication.getMenuTree().get(0).getChildren().get(0).getCode(),
                "普通用户获得页面权限后应恢复对应树分支");

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> putManifest(applicationIdA, Collections.<String>emptyList()));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_MENU_PERMISSION_REFERENCED, exception.getErrorCode());

        log.info("菜单页面权限裁剪与清单引用保护断言完成：applicationId={}", applicationIdA);
    }

    @Test
    @DisplayName("默认入口应绑定稳定菜单编码，允许同 PAGE 的静态子路由并拒绝越界路径")
    void defaultEntryUsesMenuCodeAndRejectsUnsafePath() {
        List<PortalMenuTreeNodeRequest> tree = Collections.singletonList(
                page("home", "首页", "/home", null));
        PortalIntegrationResponse saved = updatePortalConfiguration(applicationIdA, tree, "home", "/home/overview");

        assertNotNull(saved.getDefaultEntry(), "已保存的默认入口必须回显");
        assertEquals("home", saved.getDefaultEntry().getPageMenuCode());
        assertEquals("/home/overview", saved.getDefaultEntry().getPath(), "静态子路由必须保留");
        assertEquals("/home/overview", findAccessibleApplication(targetUserId, applicationCodeA)
                .getDefaultEntry().getPath(), "Portal 读模型必须给出当前用户可访问的默认入口");

        assertDefaultEntryInvalid(tree, "home", "https://example.test/escape");
        assertDefaultEntryInvalid(tree, "home", "/other");
        assertDefaultEntryInvalid(tree, "home", "/home/%252e%252e/private");
        assertDefaultEntryInvalid(tree, "missing-page", "/home");

        log.info("Portal 默认入口合法性断言完成：applicationId={}", applicationIdA);
    }

    @Test
    @DisplayName("默认入口被页面权限裁剪时应用仍可见但不能作为登录首页")
    void defaultEntryIsHiddenWhenPagePermissionIsMissing() {
        String protectedPermission = "portal:protected:read";
        putManifest(applicationIdA, Collections.singletonList(protectedPermission));
        List<PortalMenuTreeNodeRequest> tree = java.util.Arrays.asList(
                page("home", "首页", "/home", null),
                page("protected", "受限页面", "/protected", protectedPermission));
        updatePortalConfiguration(applicationIdA, tree, "protected", "/protected/dashboard");

        PortalLoginLandingRequest landing = new PortalLoginLandingRequest();
        landing.setApplicationCode(applicationCodeA);
        landing.setVersion(trustedApplicationService.getPortalLoginLanding().getVersion());
        trustedApplicationService.updatePortalLoginLanding(landing);

        PortalAccessibleApplication accessible = findAccessibleApplication(targetUserId, applicationCodeA);
        assertNull(accessible.getDefaultEntry(), "无 PAGE 权限时不得向 Portal 暴露受限默认入口");
        assertNull(portalApplicationService.getNavigationContext(targetUserId).getLoginLandingApplicationCode(),
                "全局首页不能指向当前用户无权访问的默认页面");

        log.info("默认入口权限裁剪断言完成：applicationId={}", applicationIdA);
    }

    @Test
    @DisplayName("全局登录首页需要可用默认入口，配置更新和删除均不能留下悬挂引用")
    void globalLoginLandingRequiresUsableApplicationAndIsClearedOnDelete() {
        List<PortalMenuTreeNodeRequest> tree = Collections.singletonList(
                page("home", "首页", "/home", null));
        updatePortalConfiguration(applicationIdA, tree, "home", null);

        PortalLoginLandingRequest landing = new PortalLoginLandingRequest();
        landing.setApplicationCode(applicationCodeA);
        landing.setVersion(trustedApplicationService.getPortalLoginLanding().getVersion());
        assertEquals(applicationCodeA, trustedApplicationService.updatePortalLoginLanding(landing).getApplicationCode());
        assertEquals(applicationCodeA, portalApplicationService.getNavigationContext(targetUserId)
                .getLoginLandingApplicationCode(), "普通用户可访问时应得到全局登录首页应用");

        PortalIntegrationRequest disabled = enabledPortal(applicationCodeA);
        disabled.setEnabled(false);
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationService.updateApplication(applicationIdA, updateRequest(disabled)));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_PORTAL_LOGIN_LANDING_INVALID, exception.getErrorCode(),
                "全局首页应用不得被直接停用");

        trustedApplicationService.deleteApplication(applicationIdA);
        applicationIdA = null;
        assertNull(trustedApplicationService.getPortalLoginLanding().getApplicationCode(),
                "删除全局首页应用必须同步清理单例引用");

        log.info("全局登录首页引用完整性断言完成");
    }

    @Test
    @DisplayName("Portal 配置版本陈旧时必须拒绝覆盖菜单和默认入口")
    void portalConfigurationRejectsStaleVersion() {
        List<PortalMenuTreeNodeRequest> tree = Collections.singletonList(
                page("home", "首页", "/home", null));
        PortalConfigurationRequest request = portalConfiguration(applicationIdA, tree, "home", null);
        PortalIntegrationResponse saved = trustedApplicationService.updatePortalConfiguration(applicationIdA, request);
        assertEquals("home", saved.getDefaultEntry().getPageMenuCode());

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> trustedApplicationService.updatePortalConfiguration(applicationIdA, request));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_PORTAL_CONFIGURATION_CONFLICT, exception.getErrorCode());

        log.info("Portal 配置乐观锁断言完成：applicationId={}", applicationIdA);
    }

    private void assertMenuTreeInvalid(List<PortalMenuTreeNodeRequest> menuTree) {
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> updateApplicationMenuTree(applicationIdA, menuTree));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_MENU_TREE_INVALID, exception.getErrorCode());
    }

    private PortalIntegrationResponse updatePortalConfiguration(Long applicationId,
                                                                List<PortalMenuTreeNodeRequest> tree,
                                                                String pageMenuCode, String entryPath) {
        return trustedApplicationService.updatePortalConfiguration(applicationId,
                portalConfiguration(applicationId, tree, pageMenuCode, entryPath));
    }

    private void assertDefaultEntryInvalid(List<PortalMenuTreeNodeRequest> tree, String pageMenuCode,
                                           String entryPath) {
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> updatePortalConfiguration(applicationIdA, tree, pageMenuCode, entryPath));
        assertEquals(ErrorCode.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID, exception.getErrorCode());
    }

    private PortalConfigurationRequest portalConfiguration(Long applicationId,
                                                           List<PortalMenuTreeNodeRequest> tree,
                                                           String pageMenuCode, String entryPath) {
        PortalIntegrationResponse current = trustedApplicationService.getApplication(applicationId).getPortal();
        PortalConfigurationRequest request = new PortalConfigurationRequest();
        request.setEnabled(true);
        request.setEntry(current.getEntry());
        request.setApiBase(current.getApiBase());
        request.setMenuTree(tree);
        request.setConfigVersion(current.getConfigVersion());
        PortalDefaultEntryRequest defaultEntry = new PortalDefaultEntryRequest();
        defaultEntry.setPageMenuCode(pageMenuCode);
        defaultEntry.setEntryPath(entryPath);
        request.setDefaultEntry(defaultEntry);
        return request;
    }

    private void updateApplicationMenuTree(Long applicationId, List<PortalMenuTreeNodeRequest> menuTree) {
        PortalIntegrationRequest portal = enabledPortal(applicationCodeA);
        portal.setMenuTree(menuTree);
        trustedApplicationService.updateApplication(applicationId, updateRequest(portal));
    }

    private UpdateTrustedApplicationRequest updateRequest(PortalIntegrationRequest portal) {
        UpdateTrustedApplicationRequest request = new UpdateTrustedApplicationRequest();
        request.setPortal(portal);
        return request;
    }

    private PortalIntegrationRequest enabledPortal(String applicationCode) {
        PortalIntegrationRequest portal = new PortalIntegrationRequest();
        portal.setEnabled(true);
        portal.setEntry("//localhost:9xxx/" + applicationCode + "/");
        portal.setApiBase("/" + applicationCode);
        return portal;
    }

    private PortalMenuTreeNodeRequest groupWithPage(String code, String name, PortalMenuTreeNodeRequest child) {
        PortalMenuTreeNodeRequest group = new PortalMenuTreeNodeRequest();
        group.setCode(code);
        group.setName(name);
        group.setNodeType("GROUP");
        group.setSortOrder(1);
        group.setChildren(Collections.singletonList(child));
        return group;
    }

    private PortalMenuTreeNodeRequest page(String code, String name, String route, String requiredPagePermission) {
        PortalMenuTreeNodeRequest page = new PortalMenuTreeNodeRequest();
        page.setCode(code);
        page.setName(name);
        page.setNodeType("PAGE");
        page.setRoute(route);
        page.setRequiredPagePermission(requiredPagePermission);
        page.setSortOrder(1);
        page.setChildren(Collections.emptyList());
        return page;
    }

    private MenuItemRequest legacyMenu(String code, String name, String route) {
        MenuItemRequest menu = new MenuItemRequest();
        menu.setCode(code);
        menu.setName(name);
        menu.setRoute(route);
        menu.setSortOrder(1);
        return menu;
    }

    private String longRoute() {
        StringBuilder route = new StringBuilder("/");
        while (route.length() <= 255) {
            route.append('a');
        }
        return route.toString();
    }

    private PortalAccessibleApplication findAccessibleApplication(Long userId, String applicationCode) {
        return portalApplicationService.listPortalAccessibleApplications(userId).stream()
                .filter(item -> applicationCode.equals(item.getApplicationCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("应用必须可见：" + applicationCode));
    }

    private void putManifest(Long applicationId, List<String> pagePermissions) {
        PutApplicationPermissionManifestRequest request = new PutApplicationPermissionManifestRequest();
        request.setRoles(Collections.emptyList());
        request.setPagePermissions(pagePermissions);
        request.setApiPermissions(Collections.emptyList());
        request.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, request);
    }

    private List<String> accessibleCodes(Long userId) {
        return portalApplicationService.listPortalAccessibleApplications(userId).stream()
                .map(PortalAccessibleApplication::getApplicationCode)
                .collect(Collectors.toList());
    }

    private Long createPortalApplication(String code) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(code + "-web");
        client.setClientName(code + "-web");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://" + code + ".example.test/callback"));
        client.setScopes(List.of("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        PortalIntegrationRequest portal = new PortalIntegrationRequest();
        portal.setEnabled(true);
        portal.setEntry("//localhost:9xxx/" + code + "/");
        portal.setApiBase("/" + code);
        MenuItemRequest menu = new MenuItemRequest();
        menu.setCode("home");
        menu.setName("首页");
        menu.setRoute("/home");
        menu.setSortOrder(1);
        portal.setMenus(Collections.singletonList(menu));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Portal Test " + code);
        request.setInitialClient(client);
        request.setPortal(portal);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }

    private void grantAdmitted(Long userId, Long applicationId) {
        grantAdmitted(userId, applicationId, Collections.<String>emptyList());
    }

    private void grantAdmitted(Long userId, Long applicationId, List<String> pagePermissions) {
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setUserId(userId);
        authorization.setApplicationId(applicationId);
        authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        authorization.setApiPermissionsJson(
                IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setDataGrantDocumentJson(null);
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion("manual");
        authorization.setManifestDigest("manual");
        authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setCreatedAt(Instant.now());
        authorization.setUpdatedAt(Instant.now());
        authorizationRepository.save(authorization);
    }
}
