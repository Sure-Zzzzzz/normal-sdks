package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.PermissionType;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPermissionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamPermissionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRolePermissionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.BootstrapService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
