package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.CreateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.UpdateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.request.CreateMessageRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response.MessageSendResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.UpdateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.CreateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamMessageEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserGroupEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * IAM 组织模型与站内信群发集成测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamOrganizationBroadcastServiceTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> userIds = new ArrayList<Long>();
    private final List<Long> departmentIds = new ArrayList<Long>();
    private final List<Long> groupIds = new ArrayList<Long>();

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserGroupService userGroupService;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private IamMessageRepository messageRepository;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamDepartmentRepository departmentRepository;

    @Autowired
    private IamUserGroupRepository groupRepository;

    @Autowired
    private IamUserGroupMemberRepository memberRepository;

    @MockBean
    private MessageSseService messageSseService;

    @AfterEach
    void cleanup() {
        for (Long userId : userIds) {
            messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId).forEach(messageRepository::delete);
        }
        for (Long userId : userIds) {
            memberRepository.findByUserId(userId).forEach(memberRepository::delete);
            userRepository.deleteById(userId);
        }
        for (Long groupId : groupIds) {
            memberRepository.findByGroupId(groupId).forEach(memberRepository::delete);
            groupRepository.deleteById(groupId);
        }
        List<Long> reverseDepartmentIds = new ArrayList<Long>(departmentIds);
        Collections.reverse(reverseDepartmentIds);
        for (Long departmentId : reverseDepartmentIds) {
            departmentRepository.deleteById(departmentId);
        }
    }

    @Test
    @DisplayName("部门子树应解析完整，用户可显式取消部门归属，删除受子部门和用户引用保护")
    void testDepartmentHierarchyAssignmentAndDeleteGuards() {
        IamDepartmentEntity root = createDepartment("root", "总部", null);
        IamDepartmentEntity child = createDepartment("child", "技术部", root.getId());
        IamUserEntity user = createUser("dept-user", child.getId());

        assertEquals(Arrays.asList(root.getId(), child.getId()),
                departmentService.resolveDepartmentIds(Collections.singletonList(root.getId()), true));
        assertThrows(SimpleIamServerException.class, () -> departmentService.deleteDepartment(root.getId()));
        assertThrows(SimpleIamServerException.class, () -> departmentService.deleteDepartment(child.getId()));

        UpdateUserRequest clearRequest = new UpdateUserRequest();
        clearRequest.setClearDepartment(true);
        IamUserEntity cleared = userService.updateUser(user.getId(), clearRequest);
        assertNull(cleared.getDepartmentId());
        assertDoesNotThrow(() -> departmentService.deleteDepartment(child.getId()));
        departmentIds.remove(child.getId());
        assertDoesNotThrow(() -> departmentService.deleteDepartment(root.getId()));
        departmentIds.remove(root.getId());
    }

    @Test
    @DisplayName("协作组成员分配应幂等，删除用户时应清理协作组成员关系")
    void testUserGroupMembershipLifecycle() {
        IamUserEntity user = createUser("group-user", null);
        IamUserGroupEntity group = createGroup("ops", "运营通知组");

        userGroupService.assignUser(group.getId(), user.getId());
        userGroupService.assignUser(group.getId(), user.getId());
        assertEquals(1, userGroupService.getGroupUsers(group.getId()).size());

        userService.deleteUser(user.getId());
        userIds.remove(user.getId());
        assertTrue(userGroupService.getGroupUsers(group.getId()).isEmpty());
    }

    @Test
    @DisplayName("禁用用户、部门和协作组不应作为新站内信投递目标")
    void testDisabledDeliveryTargetsAreExcluded() {
        IamDepartmentEntity activeDepartment = createDepartment("active-department", "启用部门", null);
        IamUserEntity disabledDepartmentUser = createUser("disabled-department-user", activeDepartment.getId());
        userService.disableUser(disabledDepartmentUser.getId());

        IamDepartmentEntity disabledDepartment = createDepartment("disabled-department", "禁用部门", null,
                SimpleIamServerConstant.STATUS_INACTIVE);
        IamUserEntity activeDisabledDepartmentUser = createUser("active-disabled-department-user", disabledDepartment.getId());

        IamUserGroupEntity activeGroup = createGroup("active-group", "启用协作组");
        userGroupService.assignUser(activeGroup.getId(), disabledDepartmentUser.getId());
        IamUserGroupEntity disabledGroup = createGroup("disabled-group", "禁用协作组",
                SimpleIamServerConstant.STATUS_INACTIVE);
        userGroupService.assignUser(disabledGroup.getId(), activeDisabledDepartmentUser.getId());

        assertNoRecipient(createMessageRequest(disabledDepartmentUser.getId(), null, null));
        assertNoRecipient(createMessageRequest(null, Collections.singletonList(activeDepartment.getId()), null));
        assertNoRecipient(createMessageRequest(null, Collections.singletonList(disabledDepartment.getId()), null));
        assertNoRecipient(createMessageRequest(null, null, Collections.singletonList(activeGroup.getId())));
        assertNoRecipient(createMessageRequest(null, null, Collections.singletonList(disabledGroup.getId())));
    }

    @Test
    @DisplayName("多目标发送应去重、保存发送快照，并为每位收件人推送未读数")
    void testMultiTargetBroadcastDeduplicatesAndPreservesSnapshot() {
        IamDepartmentEntity root = createDepartment("broadcast-root", "广播总部", null);
        IamDepartmentEntity child = createDepartment("broadcast-child", "广播技术部", root.getId());
        IamUserEntity rootUser = createUser("root-user", root.getId());
        IamUserEntity childUser = createUser("child-user", child.getId());
        IamUserEntity explicitUser = createUser("explicit-user", null);
        IamUserGroupEntity group = createGroup("broadcast", "广播组");
        userGroupService.assignUser(group.getId(), childUser.getId());
        userGroupService.assignUser(group.getId(), explicitUser.getId());

        CreateMessageRequest request = new CreateMessageRequest();
        request.setRecipientUserId(explicitUser.getId());
        request.setRecipientUserIds(Arrays.asList(explicitUser.getId(), childUser.getId()));
        request.setDepartmentIds(Collections.singletonList(root.getId()));
        request.setUserGroupIds(Collections.singletonList(group.getId()));
        request.setIncludeChildDepartments(true);
        request.setTitle("组织广播");
        request.setContent("发送时快照");

        MessageSendResponse response = messageService.sendMessage(request, 1L, "admin");
        assertEquals(3, response.getRecipientCount());
        assertNotNull(response.getSendBatchId());

        List<IamMessageEntity> rootMessages = messageService.listMessages(rootUser.getId());
        List<IamMessageEntity> childMessages = messageService.listMessages(childUser.getId());
        List<IamMessageEntity> explicitMessages = messageService.listMessages(explicitUser.getId());
        assertEquals(1, rootMessages.size());
        assertEquals(1, childMessages.size());
        assertEquals(1, explicitMessages.size());
        assertTrue(rootMessages.stream().allMatch(message -> response.getSendBatchId().equals(message.getSendBatchId())));
        assertTrue(childMessages.stream().allMatch(message -> response.getSendBatchId().equals(message.getSendBatchId())));
        assertTrue(explicitMessages.stream().allMatch(message -> response.getSendBatchId().equals(message.getSendBatchId())));
        assertEquals("用户:2,部门:1,协作组:1,包含子部门:true", rootMessages.get(0).getTargetSummary());
        verify(messageSseService).pushUnreadCount(rootUser.getId(), 1L);
        verify(messageSseService).pushUnreadCount(childUser.getId(), 1L);
        verify(messageSseService).pushUnreadCount(explicitUser.getId(), 1L);

        userGroupService.revokeUser(group.getId(), explicitUser.getId());
        UpdateUserRequest clearRequest = new UpdateUserRequest();
        clearRequest.setClearDepartment(true);
        userService.updateUser(childUser.getId(), clearRequest);
        assertFalse(userGroupService.getGroupUsers(group.getId()).contains(explicitUser));
        assertEquals(1, messageService.listMessages(childUser.getId()).size());
        assertEquals(1, messageService.listMessages(explicitUser.getId()).size());
    }

    @Test
    @DisplayName("显式收件人不存在时应抛 USER_NOT_FOUND，而不是空收件人错误")
    void testNonExistentExplicitRecipientThrowsUserNotFound() {
        CreateMessageRequest request = new CreateMessageRequest();
        request.setRecipientUserId(9_999_999L);
        request.setTitle("不存在的收件人");
        request.setContent("内容");
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> messageService.sendMessage(request, 1L, "admin"));
        assertEquals("USER_002", exception.getErrorCode());
    }

    @Test
    @DisplayName("includeChildDepartments=false 时只解析直接部门，不展开子部门成员")
    void testIncludeChildDepartmentsFalseOnlyResolvesDirectDepartment() {
        IamDepartmentEntity root = createDepartment("nofalse-root", "总部", null);
        IamDepartmentEntity child = createDepartment("nofalse-child", "技术部", root.getId());
        IamUserEntity rootUser = createUser("nofalse-root-user", root.getId());
        IamUserEntity childUser = createUser("nofalse-child-user", child.getId());

        CreateMessageRequest request = new CreateMessageRequest();
        request.setDepartmentIds(Collections.singletonList(root.getId()));
        request.setIncludeChildDepartments(false);
        request.setTitle("不含子部门");
        request.setContent("仅直接部门成员");

        MessageSendResponse response = messageService.sendMessage(request, 1L, "admin");
        assertEquals(1, response.getRecipientCount());
        assertEquals(1, messageService.listMessages(rootUser.getId()).size());
        assertTrue(messageService.listMessages(childUser.getId()).isEmpty());
    }

    @Test
    @DisplayName("部门父级成环时应拒绝并抛 DEPARTMENT_PARENT_INVALID")
    void testDepartmentCircularParentDetected() {
        IamDepartmentEntity root = createDepartment("cycle-root", "总部", null);
        IamDepartmentEntity child = createDepartment("cycle-child", "子部门", root.getId());

        UpdateDepartmentRequest request = new UpdateDepartmentRequest();
        request.setName(root.getName());
        request.setParentId(child.getId());
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> departmentService.updateDepartment(root.getId(), request));
        assertEquals("DEPARTMENT_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("部门编码重复时应抛 DEPARTMENT_ALREADY_EXISTS")
    void testDuplicateDepartmentCodeRejected() {
        IamDepartmentEntity first = createDepartment("dup-code", "第一部门", null);
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setCode(first.getCode());
        request.setName("第二部门");
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> departmentService.createDepartment(request));
        assertEquals("DEPARTMENT_002", exception.getErrorCode());
    }

    @Test
    @DisplayName("操作不存在的部门时应抛 DEPARTMENT_NOT_FOUND")
    void testNonExistentDepartmentThrowsNotFound() {
        Long nonExistentId = 9_999_999L;
        SimpleIamServerException getByIdException = assertThrows(SimpleIamServerException.class,
                () -> departmentService.getById(nonExistentId));
        assertEquals("DEPARTMENT_001", getByIdException.getErrorCode());

        SimpleIamServerException deleteException = assertThrows(SimpleIamServerException.class,
                () -> departmentService.deleteDepartment(nonExistentId));
        assertEquals("DEPARTMENT_001", deleteException.getErrorCode());

        CreateMessageRequest messageRequest = new CreateMessageRequest();
        messageRequest.setDepartmentIds(Collections.singletonList(nonExistentId));
        messageRequest.setTitle("部门不存在");
        messageRequest.setContent("内容");
        SimpleIamServerException sendException = assertThrows(SimpleIamServerException.class,
                () -> messageService.sendMessage(messageRequest, 1L, "admin"));
        assertEquals("DEPARTMENT_001", sendException.getErrorCode());
    }

    private IamDepartmentEntity createDepartment(String prefix, String name, Long parentId) {
        return createDepartment(prefix, name, parentId, SimpleIamServerConstant.STATUS_ACTIVE);
    }

    private IamDepartmentEntity createDepartment(String prefix, String name, Long parentId, Integer status) {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setCode(prefix + "-" + suffix);
        request.setName(name);
        request.setParentId(parentId);
        request.setStatus(status);
        IamDepartmentEntity department = departmentService.createDepartment(request);
        departmentIds.add(department.getId());
        return department;
    }

    private IamUserEntity createUser(String prefix, Long departmentId) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(prefix + "-" + suffix);
        request.setPassword("User@1234");
        request.setDisplayName("测试用户");
        request.setDepartmentId(departmentId);
        IamUserEntity user = userService.createUser(request);
        userIds.add(user.getId());
        return user;
    }

    private IamUserGroupEntity createGroup(String prefix, String name) {
        return createGroup(prefix, name, SimpleIamServerConstant.STATUS_ACTIVE);
    }

    private IamUserGroupEntity createGroup(String prefix, String name, Integer status) {
        CreateUserGroupRequest request = new CreateUserGroupRequest();
        request.setCode(prefix + "-" + suffix);
        request.setName(name);
        request.setStatus(status);
        IamUserGroupEntity group = userGroupService.createGroup(request);
        groupIds.add(group.getId());
        return group;
    }

    private CreateMessageRequest createMessageRequest(Long recipientUserId,
                                                      List<Long> departmentIds,
                                                      List<Long> groupIds) {
        CreateMessageRequest request = new CreateMessageRequest();
        request.setRecipientUserId(recipientUserId);
        request.setDepartmentIds(departmentIds);
        request.setUserGroupIds(groupIds);
        request.setTitle("禁用目标测试");
        request.setContent("禁用目标不能接收新站内信");
        return request;
    }

    private void assertNoRecipient(CreateMessageRequest request) {
        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> messageService.sendMessage(request, 1L, "admin"));
        assertEquals("MESSAGE_003", exception.getErrorCode());
    }
}
