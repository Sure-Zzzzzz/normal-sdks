package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.*;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM 组织管理 Admin API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminOrganizationApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> userIds = new ArrayList<Long>();
    private final List<Long> departmentIds = new ArrayList<Long>();
    private final List<Long> groupIds = new ArrayList<Long>();
    private final List<Long> roleIds = new ArrayList<Long>();
    private final List<Long> permissionIds = new ArrayList<Long>();
    private String adminUsername;
    private String userUsername;
    private Cookie adminSession;
    private Cookie userSession;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamDepartmentRepository departmentRepository;

    @Autowired
    private IamUserGroupRepository groupRepository;

    @Autowired
    private IamUserGroupMemberRepository memberRepository;

    @Autowired
    private IamUserRoleRepository userRoleRepository;

    @Autowired
    private IamDepartmentRoleRepository departmentRoleRepository;

    @Autowired
    private IamRoleRepository roleRepository;

    @Autowired
    private IamRolePermissionRepository rolePermissionRepository;

    @Autowired
    private IamPermissionRepository permissionRepository;

    @Autowired
    private IamMessageRepository messageRepository;

    @BeforeEach
    void loginUsers() throws Exception {
        adminUsername = "organization-admin-" + suffix;
        userUsername = "organization-user-" + suffix;
        Long adminUserId = createUserWithUsername(adminUsername, null).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(adminUserId, adminRole.getId());
        adminSession = login(adminUsername);
        createUserWithUsername(userUsername, null);
        userSession = login(userUsername);
    }

    @AfterEach
    void cleanup() {
        for (Long userId : userIds) {
            messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId).forEach(messageRepository::delete);
            memberRepository.findByUserId(userId).forEach(memberRepository::delete);
            userRoleRepository.findByUserId(userId).forEach(userRoleRepository::delete);
            userRepository.deleteById(userId);
        }
        for (Long groupId : groupIds) {
            memberRepository.findByGroupId(groupId).forEach(memberRepository::delete);
            groupRepository.deleteById(groupId);
        }
        for (Long roleId : roleIds) {
            rolePermissionRepository.findByRoleId(roleId).forEach(rolePermissionRepository::delete);
            userRoleRepository.findByRoleId(roleId).forEach(userRoleRepository::delete);
            departmentRoleRepository.findByRoleId(roleId).forEach(departmentRoleRepository::delete);
            roleRepository.deleteById(roleId);
        }
        for (Long permissionId : permissionIds) {
            rolePermissionRepository.findByPermissionId(permissionId).forEach(rolePermissionRepository::delete);
            permissionRepository.deleteById(permissionId);
        }
        List<Long> reverseDepartmentIds = new ArrayList<Long>(departmentIds);
        Collections.reverse(reverseDepartmentIds);
        for (Long departmentId : reverseDepartmentIds) {
            departmentRepository.deleteById(departmentId);
        }
    }

    @Test
    @DisplayName("Admin API 应支持部门完整 CRUD")
    void testDepartmentCrud() throws Exception {
        String rootCode = "api-root-" + suffix;
        String childCode = "api-child-" + suffix;

        mockMvc.perform(post("/iam/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + rootCode + "\",\"name\":\"API总部\",\"sortOrder\":1}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(rootCode))
                .andExpect(jsonPath("$.parentId").value(nullValue()));

        IamDepartmentEntity root = departmentRepository.findByCode(rootCode).get();
        departmentIds.add(root.getId());
        mockMvc.perform(post("/iam/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + childCode + "\",\"name\":\"API研发部\",\"parentId\":" + root.getId() + "}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(root.getId()));

        IamDepartmentEntity child = departmentRepository.findByCode(childCode).get();
        departmentIds.add(child.getId());
        mockMvc.perform(put("/iam/admin/departments/" + child.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API平台研发部\",\"parentId\":" + root.getId() + ",\"sortOrder\":2,\"status\":1}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API平台研发部"));

        mockMvc.perform(get("/iam/admin/departments/" + child.getId()).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentName").value("API总部"));

        mockMvc.perform(delete("/iam/admin/departments/" + child.getId()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        departmentIds.remove(child.getId());
        mockMvc.perform(delete("/iam/admin/departments/" + root.getId()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        departmentIds.remove(root.getId());
    }

    @Test
    @DisplayName("Admin API 应支持协作组与成员维护")
    void testUserGroupMembershipCrud() throws Exception {
        IamUserEntity user = createUser("group-member");
        String groupCode = "api-group-" + suffix;

        mockMvc.perform(post("/iam/admin/user-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + groupCode + "\",\"name\":\"API通知组\",\"description\":\"测试协作组\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(groupCode));

        IamUserGroupEntity group = groupRepository.findByCode(groupCode).get();
        groupIds.add(group.getId());
        mockMvc.perform(put("/iam/admin/user-groups/" + group.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API通知组更新\",\"description\":\"更新后的描述\",\"status\":1}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("API通知组更新"));
        mockMvc.perform(get("/iam/admin/user-groups/" + group.getId()).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("更新后的描述"));

        mockMvc.perform(post("/iam/admin/user-groups/" + group.getId() + "/users/" + user.getId())
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/iam/admin/user-groups/" + group.getId() + "/users").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(user.getId()));
        mockMvc.perform(delete("/iam/admin/user-groups/" + group.getId() + "/users/" + user.getId())
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/iam/admin/user-groups/" + group.getId()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        groupIds.remove(group.getId());
    }

    @Test
    @DisplayName("Admin API 应聚合直属成员、协作组、角色和去重后的有效权限")
    void testOrganizationWorkbenchReadApis() throws Exception {
        String rootCode = "workbench-root-" + suffix;
        String childCode = "workbench-child-" + suffix;
        IamDepartmentEntity root = createDepartment(rootCode, "工作台总部", null);
        IamDepartmentEntity child = createDepartment(childCode, "工作台研发部", root.getId());
        IamUserEntity rootUser = createUser("workbench-root", root.getId());
        IamUserEntity disabledRootUser = createUser("workbench-disabled", root.getId());
        IamUserEntity childUser = createUser("workbench-child", child.getId());
        log.info("组织工作台测试数据：rootId={}, childId={}, rootUserId={}, disabledUserId={}, childUserId={}",
                root.getId(), child.getId(), rootUser.getId(), disabledRootUser.getId(), childUser.getId());

        mockMvc.perform(put("/iam/admin/users/" + disabledRootUser.getId() + "/disable")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());

        IamUserGroupEntity group = createUserGroup("workbench-group-" + suffix, "工作台协作组");
        mockMvc.perform(post("/iam/admin/user-groups/" + group.getId() + "/users/" + rootUser.getId())
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());

        IamPermissionEntity permission = createPermission("workbench:shared:" + suffix, "工作台共享权限");
        IamRoleEntity firstRole = createRole("workbench-first:" + suffix, "工作台第一角色");
        IamRoleEntity secondRole = createRole("workbench-second:" + suffix, "工作台第二角色");
        assignPermission(firstRole.getId(), permission.getId());
        assignPermission(secondRole.getId(), permission.getId());
        assignRole(rootUser.getId(), firstRole.getId());
        assignRole(rootUser.getId(), secondRole.getId());

        mockMvc.perform(get("/iam/admin/organizations/tree").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == '" + rootCode + "')].directMemberCount").value(contains(2)))
                .andExpect(jsonPath("$[?(@.code == '" + rootCode + "')].children[?(@.code == '"
                        + childCode + "')].id").value(contains(child.getId().intValue())));
        mockMvc.perform(get("/iam/admin/organizations/departments/" + root.getId() + "/workspace").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department.id").value(root.getId()))
                .andExpect(jsonPath("$.directChildren[0].id").value(child.getId()))
                .andExpect(jsonPath("$.members.totalElements").value(2))
                .andExpect(jsonPath("$.members.page").value(1))
                .andExpect(jsonPath("$.members.number").doesNotExist())
                .andExpect(jsonPath("$.members.content[?(@.id == " + rootUser.getId() + ")].id")
                        .value(contains(rootUser.getId().intValue())));
        mockMvc.perform(get("/iam/admin/organizations/users/" + rootUser.getId() + "/profile").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.departmentId").value(root.getId()))
                .andExpect(jsonPath("$.department.id").value(root.getId()))
                .andExpect(jsonPath("$.userGroups[0].id").value(group.getId()))
                .andExpect(jsonPath("$.roles[*].code").value(contains(firstRole.getCode(), secondRole.getCode())))
                .andExpect(jsonPath("$.roles[?(@.code == '" + firstRole.getCode() + "')].source").value(contains("direct")))
                .andExpect(jsonPath("$.roles[?(@.code == '" + secondRole.getCode() + "')].source").value(contains("direct")))
                .andExpect(jsonPath("$.effectivePermissions[*].code").value(contains(permission.getCode())))
                .andExpect(jsonPath("$.effectivePermissions[?(@.code == '" + permission.getCode() + "')].source").value(contains("direct")));
    }

    @Test
    @DisplayName("Admin API 用户详情角色/权限来源应区分个人直接与部门继承，同时持有以个人直接为准")
    void testOrganizationUserProfileRoleSourceDistinguishesDirectAndDepartmentInherited() throws Exception {
        String departmentCode = "source-dept-" + suffix;
        IamDepartmentEntity department = createDepartment(departmentCode, "来源测试部门", null);
        IamUserEntity deptUser = createUser("source-user", department.getId());

        IamPermissionEntity departmentOnlyPermission = createPermission("source:department-only:" + suffix, "仅部门继承权限");
        IamRoleEntity departmentOnlyRole = createRole("source-department-only:" + suffix, "仅部门继承角色");
        assignPermission(departmentOnlyRole.getId(), departmentOnlyPermission.getId());
        assignDepartmentRole(department.getId(), departmentOnlyRole.getId());

        IamPermissionEntity mixedPermission = createPermission("source:mixed:" + suffix, "个人与部门同时持有权限");
        IamRoleEntity mixedRole = createRole("source-mixed:" + suffix, "个人与部门同时持有角色");
        assignPermission(mixedRole.getId(), mixedPermission.getId());
        assignRole(deptUser.getId(), mixedRole.getId());
        assignDepartmentRole(department.getId(), mixedRole.getId());

        mockMvc.perform(get("/iam/admin/organizations/users/" + deptUser.getId() + "/profile").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.code == '" + departmentOnlyRole.getCode() + "')].source")
                        .value(contains("department_inherited")))
                .andExpect(jsonPath("$.roles[?(@.code == '" + mixedRole.getCode() + "')].source")
                        .value(contains("direct")))
                .andExpect(jsonPath("$.effectivePermissions[?(@.code == '" + departmentOnlyPermission.getCode() + "')].source")
                        .value(contains("department_inherited")))
                .andExpect(jsonPath("$.effectivePermissions[?(@.code == '" + mixedPermission.getCode() + "')].source")
                        .value(contains("direct")));
    }

    @Test
    @DisplayName("Admin API 应支持反查挂载了指定角色的部门")
    void testRoleDepartmentsReverseLookup() throws Exception {
        IamRoleEntity role = createRole("reverse-lookup-role:" + suffix, "反查测试角色");
        IamDepartmentEntity mountedDepartment = createDepartment("reverse-lookup-dept-" + suffix, "反查测试部门", null);
        IamDepartmentEntity unmountedDepartment = createDepartment("reverse-lookup-dept-empty-" + suffix, "未挂载部门", null);

        mockMvc.perform(get("/iam/admin/roles/" + role.getId() + "/departments").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assignDepartmentRole(mountedDepartment.getId(), role.getId());

        mockMvc.perform(get("/iam/admin/roles/" + role.getId() + "/departments").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(contains(mountedDepartment.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(unmountedDepartment.getId().intValue()))));

        mockMvc.perform(delete("/iam/admin/departments/" + mountedDepartment.getId() + "/roles/" + role.getId())
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/iam/admin/roles/" + role.getId() + "/departments").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/iam/admin/roles/" + Long.MAX_VALUE + "/departments").cookie(adminSession))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin API 用户详情应稳定返回无部门与空关系集合")
    void testOrganizationUserProfileWithoutRelations() throws Exception {
        IamUserEntity user = createUser("workbench-empty");
        log.info("无关联用户详情测试：userId={}", user.getId());

        mockMvc.perform(get("/iam/admin/organizations/users/" + user.getId() + "/profile").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.department").value(nullValue()))
                .andExpect(jsonPath("$.userGroups").isEmpty())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.effectivePermissions").isEmpty());
    }

    @Test
    @DisplayName("Admin API 分页大小超过上限时应截断")
    void testAdminPageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/iam/admin/users")
                        .param("size", String.valueOf(SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE + 1))
                        .cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE));
    }

    @Test
    @DisplayName("Admin API 用户列表应支持今日登录与锁定中筛选（仪表盘下钻口径）")
    void testAdminUserListFiltersByLastLoginAndLocked() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        java.time.Instant todayStart = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant();

        IamUserEntity recentUser = createUser("filter-recent");
        recentUser.setLastLoginAt(now.minusSeconds(1));
        userRepository.save(recentUser);

        IamUserEntity yesterdayUser = createUser("filter-yesterday");
        yesterdayUser.setLastLoginAt(todayStart.minusSeconds(3600));
        userRepository.save(yesterdayUser);

        IamUserEntity lockedUser = createUser("filter-locked");
        lockedUser.setLockedUntil(now.plusSeconds(3600));
        userRepository.save(lockedUser);

        IamUserEntity neverUser = createUser("filter-never");

        mockMvc.perform(get("/iam/admin/users")
                        .param("lastLoginAfter", todayStart.toString())
                        .param("size", "500").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + recentUser.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + yesterdayUser.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.id == " + neverUser.getId() + ")]").doesNotExist());

        mockMvc.perform(get("/iam/admin/users")
                        .param("lockedUntilAfter", now.toString())
                        .param("size", "500").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + lockedUser.getId() + ")]").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + recentUser.getId() + ")]").doesNotExist());
    }

    @Test
    @DisplayName("Admin API 组织工作台读取接口应拒绝非管理员")
    void testOrganizationWorkbenchRequiresAdminAuthority() throws Exception {
        mockMvc.perform(get("/iam/admin/organizations/tree").cookie(userSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin API 群发应返回收件人数并校验消息内容")
    void testMessageSendResponseAndValidation() throws Exception {
        IamUserEntity sender = createUser("message-sender");
        IamUserEntity recipient = createUser("message-recipient");

        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipient.getId() + "],\"title\":\"API群发\",\"content\":\"消息内容\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sendBatchId").isNotEmpty())
                .andExpect(jsonPath("$.recipientCount").value(1));

        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipient.getId() + "],\"title\":\" \",\"content\":\"消息内容\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("标题不能为空")));
        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipient.getId() + "],\"title\":\"内容校验\",\"content\":\" \"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("内容不能为空")));
    }

    @Test
    @DisplayName("Admin API 站内信发送应拒绝非管理员")
    void testMessageSendRequiresAdminAuthority() throws Exception {
        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[1],\"title\":\"安全校验\",\"content\":\"安全校验内容\"}")
                        .cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("标题和内容长度边界应受控校验：等于上限通过，超限返回业务错误")
    void testMessageTitleAndContentLengthBoundary() throws Exception {
        IamUserEntity sender = createUser("boundary-sender");
        IamUserEntity recipient = createUser("boundary-recipient");
        String maxTitle = repeat("标", SimpleIamServerConstant.MESSAGE_TITLE_MAX_LENGTH);
        String maxContent = repeat("内", SimpleIamServerConstant.MESSAGE_CONTENT_MAX_LENGTH);
        String tooLongTitle = repeat("标", SimpleIamServerConstant.MESSAGE_TITLE_MAX_LENGTH + 1);
        String tooLongContent = repeat("内", SimpleIamServerConstant.MESSAGE_CONTENT_MAX_LENGTH + 1);

        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipient.getId()
                                + "],\"title\":\"" + maxTitle + "\",\"content\":\"" + maxContent + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientCount").value(1));

        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipient.getId()
                                + "],\"title\":\"" + tooLongTitle + "\",\"content\":\"正常内容\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("标题不能为空且不能超过")));

        mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipient.getId()
                                + "],\"title\":\"正常标题\",\"content\":\"" + tooLongContent + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("内容不能为空且不能超过")));
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private IamDepartmentEntity createDepartment(String code, String name, Long parentId) throws Exception {
        String parentIdJson = parentId == null ? "" : ",\"parentId\":" + parentId;
        mockMvc.perform(post("/iam/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"" + parentIdJson + "}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());
        IamDepartmentEntity department = departmentRepository.findByCode(code).get();
        departmentIds.add(department.getId());
        return department;
    }

    private IamUserGroupEntity createUserGroup(String code, String name) throws Exception {
        mockMvc.perform(post("/iam/admin/user-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());
        IamUserGroupEntity group = groupRepository.findByCode(code).get();
        groupIds.add(group.getId());
        return group;
    }

    private IamRoleEntity createRole(String code, String name) throws Exception {
        mockMvc.perform(post("/iam/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.builtIn").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        IamRoleEntity role = roleRepository.findByCode(code).get();
        roleIds.add(role.getId());
        return role;
    }

    private IamPermissionEntity createPermission(String code, String name) {
        IamPermissionEntity permission = new IamPermissionEntity();
        permission.setCode(code);
        permission.setName(name);
        permission.setType("api");
        permission.setBuiltIn(SimpleIamServerConstant.STATUS_INACTIVE);
        permission.setCreatedAt(java.time.Instant.now());
        permission.setUpdatedAt(java.time.Instant.now());
        IamPermissionEntity saved = permissionRepository.save(permission);
        permissionIds.add(saved.getId());
        return saved;
    }

    private void assignPermission(Long roleId, Long permissionId) throws Exception {
        mockMvc.perform(post("/iam/admin/roles/" + roleId + "/permissions/" + permissionId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());
    }

    private void assignRole(Long userId, Long roleId) throws Exception {
        mockMvc.perform(post("/iam/admin/users/" + userId + "/roles/" + roleId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());
    }

    private void assignDepartmentRole(Long departmentId, Long roleId) throws Exception {
        mockMvc.perform(post("/iam/admin/departments/" + departmentId + "/roles/" + roleId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());
    }

    private IamUserEntity createUser(String prefix) {
        return createUser(prefix, null);
    }

    private IamUserEntity createUser(String prefix, Long departmentId) {
        return createUserWithUsername(prefix + "-" + suffix, departmentId);
    }

    private IamUserEntity createUserWithUsername(String username, Long departmentId) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("User@1234");
        request.setDisplayName("API测试用户");
        request.setDepartmentId(departmentId);
        IamUserEntity user = userService.createUser(request);
        userIds.add(user.getId());
        return user;
    }

    private Cookie login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"User@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }
}
