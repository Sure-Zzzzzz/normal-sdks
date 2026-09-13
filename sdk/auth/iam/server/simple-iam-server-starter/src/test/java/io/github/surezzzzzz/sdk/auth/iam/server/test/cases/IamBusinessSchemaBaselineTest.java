package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.PermissionType;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.PortalMenuNodeType;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.PortalPresentationMode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageSendResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPermissionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationMenuEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import io.github.surezzzzzz.sdk.auth.iam.server.service.BootstrapService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamBusinessSchemaBaselineTest {

    @Autowired
    private IamRoleRepository roleRepository;

    @Autowired
    private IamPermissionRepository permissionRepository;

    @Autowired
    private IamRolePermissionRepository rolePermissionRepository;

    @Autowired
    private BootstrapService bootstrapService;

    @Autowired
    private SimpleIamServerProperties properties;

    @Autowired
    private IamTrustedApplicationRepository trustedApplicationRepository;

    @Autowired
    private IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;

    @Test
    @DisplayName("schema.sql 应初始化 14 个内置权限并按 page/api/data 分类")
    void testBuiltInPermissionsInitialized() {
        Map<String, IamPermissionEntity> permissions = permissionRepository.findAll().stream()
                .filter(permission -> SimpleIamServerConstant.STATUS_ACTIVE == permission.getBuiltIn())
                .collect(Collectors.toMap(IamPermissionEntity::getCode, permission -> permission));

        assertPermissionType(permissions, PermissionType.PAGE.getCode(), Arrays.asList(
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_PAGE
        ));
        assertPermissionType(permissions, PermissionType.API.getCode(), Arrays.asList(
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API
        ));
        assertPermissionType(permissions, PermissionType.DATA.getCode(), Arrays.asList(
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DATA_ALL
        ));
    }

    @Test
    @DisplayName("全量建库与 1.0 升级脚本的 Portal 单例主键应与实体类型一致")
    void portalSettingPrimaryKeyUsesIntegerSchemaType() throws IOException {
        assertTrue(readSchemaDocument("schema.sql").contains("id INT NOT NULL COMMENT '单例主键，固定为1'"),
                "全量 schema 的 Portal 单例主键必须是 INT");
        assertTrue(readSchemaDocument("migration/V1.0.0__to__V1.1.0__portal_menu_tree.sql")
                        .contains("id INT NOT NULL COMMENT '单例主键，固定为1'"),
                "1.0 升级脚本的 Portal 单例主键必须是 INT");
    }

    @Test
    @DisplayName("无上限业务计数响应应使用 long，避免累计数量溢出")
    void unboundedBusinessCountsUseLong() throws NoSuchFieldException {
        assertEquals(long.class, MessageSendResponse.class.getDeclaredField("recipientCount").getType());
        assertEquals(long.class, TrustedApplicationResponse.class.getDeclaredField("clientCount").getType());
    }

    @Test
    @DisplayName("iam_admin 应绑定 schema.sql 定义的全部内置权限")
    void testAdminRoleBoundToBuiltInPermissions() {
        IamRoleEntity adminRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).get();
        List<Long> adminPermissionIds = rolePermissionRepository.findByRoleId(adminRole.getId()).stream()
                .map(item -> item.getPermissionId())
                .collect(Collectors.toList());
        List<Long> baselinePermissionIds = baselinePermissionCodes().stream()
                .map(code -> permissionRepository.findByCode(code).get().getId())
                .collect(Collectors.toList());

        assertEquals(SimpleIamServerConstant.STATUS_ACTIVE, adminRole.getBuiltIn());
        assertTrue(adminPermissionIds.containsAll(baselinePermissionIds));
    }

    @Test
    @Transactional
    @DisplayName("iam_user 内置普通用户角色应存在且启动引导不为其绑定权限")
    void testUserRoleExistsWithoutPermissions() {
        IamRoleEntity userRole = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_USER).get();

        assertEquals(SimpleIamServerConstant.STATUS_ACTIVE, userRole.getBuiltIn());
        // 管理面手工给 iam_user 绑权限是合法操作，库内终态不反映引导行为；
        // 回滚事务内清扫手工绑定后重放启动引导，断言引导本身不补绑定。
        rolePermissionRepository.deleteByRoleId(userRole.getId());
        bootstrapService.run(null);
        assertTrue(rolePermissionRepository.findByRoleId(userRole.getId()).isEmpty(),
                "iam_user 是无管理权限的基础身份角色，启动引导不应为其绑定权限");
    }

    @Test
    @Transactional
    @DisplayName("首次引导应递归创建内置菜单树，重复启动保持幂等")
    void bootstrapCreatesMenuTreeForNewApplication() {
        String applicationCode = "bootstrap-menu-tree-" + UUID.randomUUID().toString().substring(0, 8);
        SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig application =
                new SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig();
        application.setApplicationCode(applicationCode);
        application.setApplicationName("菜单树引导测试应用");
        application.setEntry("https://portal.example.test/");
        application.setMenuTree(Collections.singletonList(group("directory", "目录", Collections.singletonList(
                page("member", "成员", "/members", null)))));
        properties.getBootstrap().getBuiltInApplications().add(application);
        try {
            bootstrapService.run(null);
            IamTrustedApplicationEntity saved = trustedApplicationRepository.findByApplicationCode(applicationCode)
                    .orElseThrow(() -> new AssertionError("首次引导必须创建应用"));
            assertMenuTree(saved.getId());

            bootstrapService.run(null);
            assertEquals(2, trustedApplicationMenuRepository.findByApplicationIdOrderBySortOrderAsc(saved.getId()).size(),
                    "重复启动不能重复创建菜单节点");
        } finally {
            properties.getBootstrap().getBuiltInApplications().remove(application);
        }
    }

    @Test
    @Transactional
    @DisplayName("非法内置菜单配置应在完整校验后回滚，不能写入半棵树")
    void bootstrapRejectsInvalidMenuTreeAtomically() {
        String applicationCode = "bootstrap-invalid-menu-" + UUID.randomUUID().toString().substring(0, 8);
        SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig application =
                new SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationConfig();
        application.setApplicationCode(applicationCode);
        application.setApplicationName("非法菜单引导测试应用");
        application.setEntry("https://portal.example.test/");
        application.setMenuTree(Collections.singletonList(page("invalid", "非法页面", "/%2e%2e/private", null)));
        properties.getBootstrap().getBuiltInApplications().add(application);
        try {
            assertThrows(SimpleIamServerException.class, () -> bootstrapService.run(null));
            IamTrustedApplicationEntity saved = trustedApplicationRepository.findByApplicationCode(applicationCode)
                    .orElseThrow(() -> new AssertionError("当前测试事务必须可观察到引导中的应用对象"));
            assertTrue(trustedApplicationMenuRepository.findByApplicationIdOrderBySortOrderAsc(saved.getId()).isEmpty(),
                    "非法配置不能留下任何菜单节点或半棵菜单树");
        } finally {
            properties.getBootstrap().getBuiltInApplications().remove(application);
        }
    }

    @Test
    @Transactional
    @DisplayName("未定制的 IAM 1.0 默认菜单应升级为分组树，重复启动保持幂等")
    void bootstrapUpgradesExactLegacyIamMenu() {
        IamTrustedApplicationEntity application = requireIamApplication();
        replaceWithLegacyIamMenus(application.getId());

        bootstrapService.run(null);
        assertIamDefaultMenuTree(application.getId());

        bootstrapService.run(null);
        assertIamDefaultMenuTree(application.getId());
        assertEquals(9, trustedApplicationMenuRepository.findByApplicationIdOrderBySortOrderAsc(application.getId()).size(),
                "重复启动不得重复创建 IAM 菜单节点");
    }

    @Test
    @Transactional
    @DisplayName("已定制的 IAM 菜单不能被启动引导覆盖")
    void bootstrapPreservesCustomizedIamMenu() {
        IamTrustedApplicationEntity application = requireIamApplication();
        replaceWithLegacyIamMenus(application.getId());
        List<IamTrustedApplicationMenuEntity> menus = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(application.getId());
        menus.get(1).setName("成员目录");
        menus.get(1).setIcon("folder");
        trustedApplicationMenuRepository.save(menus.get(1));

        bootstrapService.run(null);

        List<IamTrustedApplicationMenuEntity> after = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(application.getId());
        assertEquals(7, after.size(), "已定制菜单不能被替换为默认分组树");
        assertEquals("成员目录", after.get(1).getName(), "管理员修改的菜单文案必须保留");
        assertEquals("folder", after.get(1).getIcon(), "管理员配置的菜单图标必须保留");
        assertTrue(after.stream().allMatch(menu -> menu.getNodeType() == PortalMenuNodeType.PAGE
                        && menu.getParentId() == null),
                "已定制的历史扁平菜单必须原样保留");
    }

    private IamTrustedApplicationEntity requireIamApplication() {
        return trustedApplicationRepository.findByApplicationCode(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM)
                .orElseThrow(() -> new AssertionError("启动基线必须已创建 IAM 内置应用"));
    }

    private void replaceWithLegacyIamMenus(Long applicationId) {
        trustedApplicationMenuRepository.deleteByApplicationId(applicationId);
        for (int index = 0; index < legacyIamMenus().size(); index++) {
            LegacyMenu item = legacyIamMenus().get(index);
            IamTrustedApplicationMenuEntity menu = new IamTrustedApplicationMenuEntity();
            menu.setApplicationId(applicationId);
            menu.setCode(item.code);
            menu.setName(item.name);
            menu.setNodeType(PortalMenuNodeType.PAGE);
            menu.setRoute(item.route);
            menu.setSortOrder(index);
            trustedApplicationMenuRepository.save(menu);
        }
        trustedApplicationMenuRepository.flush();
    }

    private List<LegacyMenu> legacyIamMenus() {
        return Arrays.asList(
                new LegacyMenu("dashboard", "仪表盘", "/"),
                new LegacyMenu("users", "用户管理", "/users"),
                new LegacyMenu("organizations", "组织与成员", "/organizations"),
                new LegacyMenu("user-groups", "协作组管理", "/user-groups"),
                new LegacyMenu("roles", "角色管理", "/roles"),
                new LegacyMenu("trusted-applications", "可信应用", "/trusted-applications"),
                new LegacyMenu("messages", "站内信", "/messages"));
    }

    private SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig group(
            String code, String name, List<SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig> children) {
        SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig group =
                new SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig();
        group.setCode(code);
        group.setName(name);
        group.setNodeType(PortalMenuNodeType.GROUP);
        group.setChildren(new ArrayList<>(children));
        return group;
    }

    private SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig page(
            String code, String name, String route, String requiredPagePermission) {
        SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig page =
                new SimpleIamServerProperties.BootstrapConfig.BuiltInApplicationMenuConfig();
        page.setCode(code);
        page.setName(name);
        page.setNodeType(PortalMenuNodeType.PAGE);
        page.setRoute(route);
        page.setRequiredPagePermission(requiredPagePermission);
        return page;
    }

    private void assertMenuTree(Long applicationId) {
        List<IamTrustedApplicationMenuEntity> menus = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(applicationId);
        assertEquals(2, menus.size(), "树引导应保存分组与页面两个节点");
        IamTrustedApplicationMenuEntity group = menus.stream()
                .filter(menu -> "directory".equals(menu.getCode()))
                .findFirst().orElseThrow(() -> new AssertionError("缺少根分组"));
        IamTrustedApplicationMenuEntity page = menus.stream()
                .filter(menu -> "member".equals(menu.getCode()))
                .findFirst().orElseThrow(() -> new AssertionError("缺少页面节点"));
        assertEquals(PortalMenuNodeType.GROUP, group.getNodeType());
        assertEquals(PortalMenuNodeType.PAGE, page.getNodeType());
        assertEquals(PortalPresentationMode.STANDARD, group.getPresentationMode(), "GROUP 必须落标准展示模式");
        assertEquals(PortalPresentationMode.STANDARD, page.getPresentationMode(), "未配置 PAGE 必须保留历史标准布局");
        assertEquals(group.getId(), page.getParentId(), "页面必须挂在分组下");
    }

    private void assertIamDefaultMenuTree(Long applicationId) {
        List<IamTrustedApplicationMenuEntity> menus = trustedApplicationMenuRepository
                .findByApplicationIdOrderBySortOrderAsc(applicationId);
        assertEquals(9, menus.size(), "IAM 默认菜单树应包含四个根节点和五个页面子节点");
        Map<String, IamTrustedApplicationMenuEntity> byCode = menus.stream()
                .collect(Collectors.toMap(IamTrustedApplicationMenuEntity::getCode, menu -> menu));
        assertEquals(PortalMenuNodeType.GROUP, byCode.get("identity-directory").getNodeType());
        assertEquals(PortalMenuNodeType.GROUP, byCode.get("access-control").getNodeType());
        assertNull(byCode.get("identity-directory").getIcon());
        assertNull(byCode.get("access-control").getIcon());
        assertEquals("dashboard", byCode.get("dashboard").getIcon());
        assertEquals("users", byCode.get("users").getIcon());
        assertEquals("folder", byCode.get("organizations").getIcon());
        assertEquals("workflow", byCode.get("user-groups").getIcon());
        assertEquals("network", byCode.get("trusted-applications").getIcon());
        assertNull(byCode.get("messages").getIcon());
        assertEquals(PortalPresentationMode.IMMERSIVE, byCode.get("dashboard").getPresentationMode(),
                "内置仪表盘应作为无边框总览页展示");
        assertEquals(PortalPresentationMode.STANDARD, byCode.get("users").getPresentationMode(),
                "仪表盘下钻的管理页必须恢复门户导航壳");
        assertEquals(byCode.get("identity-directory").getId(), byCode.get("users").getParentId());
        assertEquals(byCode.get("identity-directory").getId(), byCode.get("organizations").getParentId());
        assertEquals(byCode.get("identity-directory").getId(), byCode.get("user-groups").getParentId());
        assertEquals(byCode.get("access-control").getId(), byCode.get("roles").getParentId());
        assertEquals(byCode.get("access-control").getId(), byCode.get("trusted-applications").getParentId());
        assertEquals(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE,
                byCode.get("users").getRequiredPagePermission());
        assertEquals(SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_PAGE,
                byCode.get("organizations").getRequiredPagePermission());
        assertEquals(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_PAGE,
                byCode.get("user-groups").getRequiredPagePermission());
        assertEquals(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_PAGE,
                byCode.get("roles").getRequiredPagePermission());
        assertEquals(SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE,
                byCode.get("trusted-applications").getRequiredPagePermission());
        assertEquals(SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_PAGE,
                byCode.get("messages").getRequiredPagePermission());
    }

    private void assertPermissionType(Map<String, IamPermissionEntity> permissions, String expectedType, List<String> codes) {
        for (String code : codes) {
            assertTrue(permissions.containsKey(code), "缺少内置权限：" + code);
            assertEquals(expectedType, permissions.get(code).getType(), "内置权限类型不正确：" + code);
        }
    }

    private List<String> baselinePermissionCodes() {
        return Arrays.asList(
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DATA_ALL
        );
    }

    private String readSchemaDocument(String relativePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get("docs", relativePath)), StandardCharsets.UTF_8);
    }

    private static final class LegacyMenu {

        private final String code;
        private final String name;
        private final String route;

        private LegacyMenu(String code, String name, String route) {
            this.code = code;
            this.name = name;
            this.route = route;
        }
    }
}
