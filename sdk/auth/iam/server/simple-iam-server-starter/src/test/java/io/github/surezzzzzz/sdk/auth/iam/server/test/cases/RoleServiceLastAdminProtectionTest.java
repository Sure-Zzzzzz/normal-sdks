package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import io.github.surezzzzzz.sdk.auth.iam.server.service.DepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamAuthorizationProjectionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamEffectiveRoleResolver;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * IAM 最后管理员保护测试（纯 mock，不起 Spring 上下文）。
 *
 * <p>覆盖 RoleService 的 iam_admin 角色最后可用管理员判定：撤销唯一 active
 * 管理员的 iam_admin 角色被拒；存在其他 active 管理员或撤销的是普通角色时放行。
 * UserService 删除 / 禁用用户走同一判定入口（assertNotLastActiveAdmin），不重复测。
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class RoleServiceLastAdminProtectionTest {

    private static final Long ADMIN_ROLE_ID = 1L;
    private static final Long NORMAL_ROLE_ID = 2L;
    private static final Long TARGET_USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;

    @Mock
    private IamRoleRepository roleRepository;

    @Mock
    private IamUserRoleRepository userRoleRepository;

    @Mock
    private IamDepartmentRoleRepository departmentRoleRepository;

    @Mock
    private IamEffectiveRoleResolver effectiveRoleResolver;

    @Mock
    private DepartmentService departmentService;

    @Mock
    private IamPermissionRepository permissionRepository;

    @Mock
    private IamRolePermissionRepository rolePermissionRepository;

    @Mock
    private IamUserRepository userRepository;

    @Mock
    private IamAuditEventPublisher auditEventPublisher;

    @Mock
    private IamRoleAuthorizationRuleRepository ruleRepository;

    @Mock
    private IamAuthorizationProjectionService projectionService;

    @InjectMocks
    private RoleService roleService;

    @Test
    void shouldRejectRevokingAdminRoleFromLastActiveAdmin() {
        mockAdminRole();
        mockAdminBindings(binding(TARGET_USER_ID));
        IamUserEntity target = new IamUserEntity();
        target.setId(TARGET_USER_ID);
        target.setUsername("only-admin");
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(target));

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> roleService.revokeRole(TARGET_USER_ID, ADMIN_ROLE_ID));

        assertTrue(exception.getMessage().contains("最后一个可用 IAM 管理员"));
        assertTrue(exception.getMessage().contains("only-admin"));
        verify(userRoleRepository, never()).deleteByUserIdAndRoleId(anyLong(), anyLong());
    }

    @Test
    void shouldAllowRevokingWhenOtherActiveAdminExists() {
        mockAdminRole();
        mockAdminBindings(binding(TARGET_USER_ID), binding(OTHER_USER_ID));
        mockTargetHoldingAdminRole();
        IamUserEntity other = new IamUserEntity();
        other.setId(OTHER_USER_ID);
        other.setUsername("other-admin");
        when(userRepository.findAllById(anyCollection())).thenReturn(Collections.singletonList(other));

        assertDoesNotThrow(() -> roleService.revokeRole(TARGET_USER_ID, ADMIN_ROLE_ID));
        verify(userRoleRepository).deleteByUserIdAndRoleId(TARGET_USER_ID, ADMIN_ROLE_ID);
    }

    @Test
    void shouldRejectWhenOtherAdminBindingExistsButUserInactive() {
        mockAdminRole();
        mockAdminBindings(binding(TARGET_USER_ID), binding(OTHER_USER_ID));
        IamUserEntity other = new IamUserEntity();
        other.setId(OTHER_USER_ID);
        other.setUsername("other-admin");
        other.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        when(userRepository.findAllById(anyCollection())).thenReturn(Collections.singletonList(other));

        assertThrows(SimpleIamServerException.class,
                () -> roleService.revokeRole(TARGET_USER_ID, ADMIN_ROLE_ID));
        verify(userRoleRepository, never()).deleteByUserIdAndRoleId(anyLong(), anyLong());
    }

    @Test
    void shouldAllowRevokingNonAdminRoleFromLastActiveAdmin() {
        IamRoleEntity normalRole = new IamRoleEntity();
        normalRole.setId(NORMAL_ROLE_ID);
        normalRole.setCode("viewer");
        when(roleRepository.findById(NORMAL_ROLE_ID)).thenReturn(Optional.of(normalRole));
        when(userRoleRepository.findByUserId(TARGET_USER_ID)).thenReturn(List.of(binding(TARGET_USER_ID)));

        assertDoesNotThrow(() -> roleService.revokeRole(TARGET_USER_ID, NORMAL_ROLE_ID));
        verify(userRoleRepository).deleteByUserIdAndRoleId(TARGET_USER_ID, NORMAL_ROLE_ID);
        verify(roleRepository, never()).findByCode(org.mockito.ArgumentMatchers.anyString());
    }

    private void mockAdminRole() {
        IamRoleEntity adminRole = new IamRoleEntity();
        adminRole.setId(ADMIN_ROLE_ID);
        adminRole.setCode("iam_admin");
        when(roleRepository.findById(ADMIN_ROLE_ID)).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByCode("iam_admin")).thenReturn(Optional.of(adminRole));
    }

    private void mockAdminBindings(IamUserRoleEntity... bindings) {
        Set<Long> holderIds = Arrays.stream(bindings)
                .map(IamUserRoleEntity::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        when(effectiveRoleResolver.resolveUserIdsByRoleId(ADMIN_ROLE_ID)).thenReturn(holderIds);
    }

    private void mockTargetHoldingAdminRole() {
        when(userRoleRepository.findByUserId(TARGET_USER_ID)).thenReturn(List.of(binding(TARGET_USER_ID)));
    }

    private IamUserRoleEntity binding(Long userId) {
        IamUserRoleEntity entity = new IamUserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(ADMIN_ROLE_ID);
        return entity;
    }
}
