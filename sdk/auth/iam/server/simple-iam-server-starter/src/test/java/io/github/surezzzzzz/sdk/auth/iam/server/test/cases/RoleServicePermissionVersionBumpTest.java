package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 权限版本 bump 覆盖测试（纯 mock，不起 Spring 上下文）。
 *
 * <p>热刷新生效前提：每个实际改变用户有效权限的写路径都必须 bump 受影响用户的
 * permission_version，幂等空转（重复分配、撤销未持有项）不得 bump。本用例逐一
 * 锁定各写路径的 bump 调用与目标用户集合。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class RoleServicePermissionVersionBumpTest {

    private static final Long ROLE_ID = 5L;
    private static final Long PERMISSION_ID = 7L;
    private static final Long USER_ID = 10L;
    private static final Long MEMBER_ONE = 11L;
    private static final Long MEMBER_TWO = 12L;

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
    void shouldBumpUserOnActualRoleAssignment() {
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

        roleService.assignRole(USER_ID, ROLE_ID);

        verify(userRepository).bumpPermissionVersion(List.of(USER_ID));
    }

    @Test
    void shouldNotBumpOnIdempotentRoleAssignment() {
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(binding(USER_ID)));

        roleService.assignRole(USER_ID, ROLE_ID);

        verify(userRepository, never()).bumpPermissionVersion(anyList());
    }

    @Test
    void shouldBumpUserOnActualRoleRevocation() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole()));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(List.of(binding(USER_ID)));

        roleService.revokeRole(USER_ID, ROLE_ID);

        verify(userRepository).bumpPermissionVersion(List.of(USER_ID));
    }

    @Test
    void shouldNotBumpWhenRevokingUnheldRole() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole()));
        when(userRoleRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

        roleService.revokeRole(USER_ID, ROLE_ID);

        verify(userRepository, never()).bumpPermissionVersion(anyList());
    }

    @Test
    void shouldBumpAllRoleMembersOnActualPermissionAssignment() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole()));
        when(permissionRepository.findById(PERMISSION_ID)).thenReturn(Optional.of(permission()));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(Collections.emptyList());
        mockRoleHolders();

        roleService.assignPermission(ROLE_ID, PERMISSION_ID);

        verify(userRepository).bumpPermissionVersion(List.of(MEMBER_ONE, MEMBER_TWO));
    }

    @Test
    void shouldNotBumpOnIdempotentPermissionAssignment() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole()));
        when(permissionRepository.findById(PERMISSION_ID)).thenReturn(Optional.of(permission()));
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(rolePermission()));

        roleService.assignPermission(ROLE_ID, PERMISSION_ID);

        verify(userRepository, never()).bumpPermissionVersion(anyList());
    }

    @Test
    void shouldBumpAllRoleMembersOnActualPermissionRevocation() {
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(rolePermission()));
        mockRoleHolders();

        roleService.revokePermission(ROLE_ID, PERMISSION_ID);

        verify(userRepository).bumpPermissionVersion(List.of(MEMBER_ONE, MEMBER_TWO));
    }

    @Test
    void shouldNotBumpWhenRevokingUnassignedPermission() {
        when(rolePermissionRepository.findByRoleId(ROLE_ID)).thenReturn(Collections.emptyList());

        roleService.revokePermission(ROLE_ID, PERMISSION_ID);

        verify(userRepository, never()).bumpPermissionVersion(anyList());
    }

    @Test
    void shouldBumpAllMembersOnRoleDeletion() {
        IamRoleEntity role = customRole();
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(role));
        // 个人直接绑定仍走 iam_user_role 逐条清理；bump 目标集合以有效角色持有者（含部门继承）为准
        when(userRoleRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(binding(MEMBER_ONE), binding(MEMBER_TWO)));
        mockRoleHolders();

        roleService.deleteRole(ROLE_ID);

        verify(userRepository).bumpPermissionVersion(List.of(MEMBER_ONE, MEMBER_TWO));
    }

    @Test
    void shouldNotBumpWhenDeletingBuiltInRole() {
        IamRoleEntity builtIn = customRole();
        builtIn.setBuiltIn(SimpleIamServerConstant.STATUS_ACTIVE);
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(builtIn));

        assertThrows(SimpleIamServerException.class, () -> roleService.deleteRole(ROLE_ID));

        verify(userRepository, never()).bumpPermissionVersion(anyList());
    }

    @Test
    void shouldPageRoleMembersWithUserDetails() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole()));
        List<IamUserRoleEntity> bindings = List.of(binding(MEMBER_ONE), binding(MEMBER_TWO));
        Pageable pageable = PageRequest.of(0, 20);
        when(userRoleRepository.findByRoleId(eq(ROLE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(bindings, pageable, 2));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(user(MEMBER_ONE, "member-one"), user(MEMBER_TWO, "member-two")));

        Page<IamUserEntity> members = roleService.listRoleMembers(ROLE_ID, 1, 20);

        assertEquals(2, members.getTotalElements());
        assertEquals(List.of(MEMBER_ONE, MEMBER_TWO),
                members.getContent().stream().map(IamUserEntity::getId).collect(Collectors.toList()));
    }

    @Test
    void shouldReturnEmptyMemberPageWhenRoleHasNoMembers() {
        when(roleRepository.findById(ROLE_ID)).thenReturn(Optional.of(customRole()));
        Pageable pageable = PageRequest.of(0, 20);
        when(userRoleRepository.findByRoleId(eq(ROLE_ID), any(Pageable.class)))
                .thenReturn(Page.empty(pageable));

        Page<IamUserEntity> members = roleService.listRoleMembers(ROLE_ID, 1, 20);

        assertEquals(0, members.getTotalElements());
        verify(userRepository, never()).findAllById(anyCollection());
    }

    /**
     * stub 有效角色持有者（个人直接 ∪ 部门继承）为两位成员；LinkedHashSet 保序，
     * 使 bump 目标集合 new ArrayList<>(set) 与 verify 的 List.of(MEMBER_ONE, MEMBER_TWO) 顺序一致
     */
    private void mockRoleHolders() {
        when(effectiveRoleResolver.resolveUserIdsByRoleId(ROLE_ID))
                .thenReturn(new LinkedHashSet<>(List.of(MEMBER_ONE, MEMBER_TWO)));
    }

    private IamRoleEntity customRole() {
        IamRoleEntity role = new IamRoleEntity();
        role.setId(ROLE_ID);
        role.setCode("business_operator");
        role.setName("业务运营");
        role.setBuiltIn(SimpleIamServerConstant.STATUS_INACTIVE);
        return role;
    }

    private IamPermissionEntity permission() {
        IamPermissionEntity permission = new IamPermissionEntity();
        permission.setId(PERMISSION_ID);
        permission.setCode("iam:user:api");
        permission.setName("IAM 用户管理接口");
        permission.setType("api");
        return permission;
    }

    private IamUserRoleEntity binding(Long userId) {
        IamUserRoleEntity entity = new IamUserRoleEntity();
        entity.setUserId(userId);
        entity.setRoleId(ROLE_ID);
        return entity;
    }

    private IamRolePermissionEntity rolePermission() {
        IamRolePermissionEntity entity = new IamRolePermissionEntity();
        entity.setRoleId(ROLE_ID);
        entity.setPermissionId(PERMISSION_ID);
        return entity;
    }

    private IamUserEntity user(Long id, String username) {
        IamUserEntity user = new IamUserEntity();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
