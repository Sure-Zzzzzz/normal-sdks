package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.CreateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.usergroup.request.UpdateUserGroupRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserGroupEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserGroupMemberEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserGroupMemberRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserGroupRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 协作组管理服务
 *
 * <p>协作组写操作与成员分配发布管理面审计事件；成员分配以协作组为主体。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class UserGroupService {

    private final IamUserGroupRepository groupRepository;
    private final IamUserGroupMemberRepository memberRepository;
    private final IamUserRepository userRepository;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 查询所有协作组
     */
    public List<IamUserGroupEntity> getAllGroups() {
        return groupRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * 管理台分页查询协作组
     */
    public Page<IamUserGroupEntity> listGroups(Integer status, String keyword, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return groupRepository.searchForConsole(status, normalizedKeyword, pageable);
    }

    /**
     * 创建协作组
     */
    @Transactional
    public IamUserGroupEntity createGroup(CreateUserGroupRequest request) {
        String code = normalizeRequired(request.getCode(), ServerErrorMessage.USER_GROUP_CODE_EMPTY);
        if (groupRepository.existsByCode(code)) {
            throw new SimpleIamServerException(ErrorCode.USER_GROUP_ALREADY_EXISTS,
                    String.format(ServerErrorMessage.USER_GROUP_ALREADY_EXISTS, code));
        }
        IamUserGroupEntity group = new IamUserGroupEntity();
        group.setCode(code);
        group.setName(normalizeRequired(request.getName(), ServerErrorMessage.USER_GROUP_NAME_EMPTY));
        group.setDescription(normalizeOptional(request.getDescription()));
        group.setStatus(resolveStatus(request.getStatus()));
        group.setCreatedAt(Instant.now());
        group.setUpdatedAt(Instant.now());
        IamUserGroupEntity saved = groupRepository.save(group);
        log.info("协作组创建成功：code={}, id={}", saved.getCode(), saved.getId());
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.USER_GROUP,
                String.valueOf(saved.getId()), saved.getCode(), null);
        return saved;
    }

    /**
     * 更新协作组
     */
    @Transactional
    public IamUserGroupEntity updateGroup(Long groupId, UpdateUserGroupRequest request) {
        IamUserGroupEntity group = getById(groupId);
        group.setName(normalizeRequired(request.getName(), ServerErrorMessage.USER_GROUP_NAME_EMPTY));
        group.setDescription(normalizeOptional(request.getDescription()));
        group.setStatus(resolveStatus(request.getStatus()));
        group.setUpdatedAt(Instant.now());
        IamUserGroupEntity saved = groupRepository.save(group);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.USER_GROUP,
                String.valueOf(groupId), saved.getCode(), null);
        return saved;
    }

    /**
     * 删除协作组
     */
    @Transactional
    public void deleteGroup(Long groupId) {
        IamUserGroupEntity group = getById(groupId);
        memberRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);
        log.info("协作组删除成功：code={}, id={}", group.getCode(), groupId);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.USER_GROUP,
                String.valueOf(groupId), group.getCode(), null);
    }

    /**
     * 分配协作组成员
     */
    @Transactional
    public void assignUser(Long groupId, Long userId) {
        IamUserGroupEntity group = getById(groupId);
        getUserById(userId);
        if (memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            return;
        }
        IamUserGroupMemberEntity member = new IamUserGroupMemberEntity();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setCreatedAt(Instant.now());
        memberRepository.save(member);
        auditEventPublisher.publishAdminAction(AdminActionType.ASSIGNED, AdminSubjectType.USER_GROUP,
                String.valueOf(groupId), group.getCode(), "userId=" + userId);
    }

    /**
     * 撤销协作组成员
     */
    @Transactional
    public void revokeUser(Long groupId, Long userId) {
        memberRepository.deleteByGroupIdAndUserId(groupId, userId);
        auditEventPublisher.publishAdminAction(AdminActionType.UNASSIGNED, AdminSubjectType.USER_GROUP,
                String.valueOf(groupId), null, "userId=" + userId);
    }

    /**
     * 查询协作组成员
     */
    public List<IamUserEntity> getGroupUsers(Long groupId) {
        getById(groupId);
        List<Long> userIds = memberRepository.findByGroupId(groupId).stream()
                .map(IamUserGroupMemberEntity::getUserId)
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return userRepository.findAllById(userIds);
    }

    /**
     * 查询用户所属协作组
     *
     * @param userId 用户 ID
     * @return 按协作组编码升序排列的所属协作组
     */
    public List<IamUserGroupEntity> getUserGroups(Long userId) {
        List<Long> groupIds = memberRepository.findByUserId(userId).stream()
                .map(IamUserGroupMemberEntity::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return Collections.emptyList();
        }
        return groupRepository.findAllById(groupIds).stream()
                .sorted(Comparator.comparing(IamUserGroupEntity::getCode))
                .collect(Collectors.toList());
    }

    /**
     * 解析启用协作组成员用户 ID，用于发送站内信
     */
    public List<Long> resolveActiveMemberUserIds(Collection<Long> groupIds) {
        List<Long> normalizedGroupIds = normalizeIds(groupIds);
        if (normalizedGroupIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> activeGroupIds = new ArrayList<Long>();
        for (Long groupId : normalizedGroupIds) {
            IamUserGroupEntity group = getById(groupId);
            if (SimpleIamServerConstant.STATUS_ACTIVE == group.getStatus()) {
                activeGroupIds.add(groupId);
            }
        }
        if (activeGroupIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new LinkedHashSet<Long>();
        for (IamUserGroupMemberEntity member : memberRepository.findByGroupIdIn(activeGroupIds)) {
            userIds.add(member.getUserId());
        }
        return new ArrayList<Long>(userIds);
    }

    /**
     * 根据 ID 查询协作组，不存在则抛异常
     */
    public IamUserGroupEntity getById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.USER_GROUP_NOT_FOUND,
                        String.format(ServerErrorMessage.USER_GROUP_NOT_FOUND, groupId)));
    }

    private IamUserEntity getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.USER_NOT_FOUND,
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId)));
    }

    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> normalizedIds = new LinkedHashSet<Long>();
        for (Long id : ids) {
            if (id != null) {
                normalizedIds.add(id);
            }
        }
        return new ArrayList<Long>(normalizedIds);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED, message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private int resolveStatus(Integer status) {
        return status == null ? SimpleIamServerConstant.STATUS_ACTIVE : status;
    }
}
