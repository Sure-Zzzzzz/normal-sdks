package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.CreateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.UpdateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamDepartmentRepository;
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
 * 部门管理服务
 *
 * <p>部门写操作发布管理面审计事件。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class DepartmentService {

    private final IamDepartmentRepository departmentRepository;
    private final IamUserRepository userRepository;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 查询所有部门
     */
    public List<IamDepartmentEntity> getAllDepartments() {
        return departmentRepository.findAll(Sort.by(Sort.Direction.ASC, "sortOrder", "id"));
    }

    /**
     * 按 ID 批量查询部门（角色反查挂载部门等场景复用）
     */
    public List<IamDepartmentEntity> getByIds(Collection<Long> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Collections.emptyList();
        }
        return departmentRepository.findAllById(departmentIds).stream()
                .sorted(Comparator.comparing(IamDepartmentEntity::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(IamDepartmentEntity::getId))
                .collect(Collectors.toList());
    }

    /**
     * 管理台分页查询部门
     */
    public Page<IamDepartmentEntity> listDepartments(Integer status, String keyword, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "sortOrder", "id"));
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return departmentRepository.searchForConsole(status, normalizedKeyword, pageable);
    }

    /**
     * 创建部门
     */
    @Transactional
    public IamDepartmentEntity createDepartment(CreateDepartmentRequest request) {
        String code = normalizeRequired(request.getCode(), ServerErrorMessage.DEPARTMENT_CODE_EMPTY);
        if (departmentRepository.existsByCode(code)) {
            throw new SimpleIamServerException(ErrorCode.DEPARTMENT_ALREADY_EXISTS,
                    String.format(ServerErrorMessage.DEPARTMENT_ALREADY_EXISTS, code));
        }
        validateParent(request.getParentId(), null);
        IamDepartmentEntity department = new IamDepartmentEntity();
        department.setCode(code);
        department.setName(normalizeRequired(request.getName(), ServerErrorMessage.DEPARTMENT_NAME_EMPTY));
        department.setParentId(request.getParentId());
        department.setSortOrder(resolveSortOrder(request.getSortOrder()));
        department.setStatus(resolveStatus(request.getStatus()));
        department.setCreatedAt(Instant.now());
        department.setUpdatedAt(Instant.now());
        IamDepartmentEntity saved = departmentRepository.save(department);
        bindMembers(saved, request.getMemberIds());
        log.info("部门创建成功：code={}, id={}", saved.getCode(), saved.getId());
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.DEPARTMENT,
                String.valueOf(saved.getId()), saved.getCode(), null);
        return saved;
    }

    /**
     * 创建部门时同步绑定已有成员：同一事务内把成员挂到新部门，
     * 任一成员不存在则整体回滚（部门也不落库）。
     */
    private void bindMembers(IamDepartmentEntity department, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        for (Long memberId : memberIds) {
            IamUserEntity member = userRepository.findById(memberId)
                    .orElseThrow(() -> new SimpleIamServerException(ErrorCode.USER_NOT_FOUND,
                            String.format(ServerErrorMessage.USER_NOT_FOUND, memberId)));
            member.setDepartmentId(department.getId());
            member.setUpdatedAt(Instant.now());
            userRepository.save(member);
            auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.USER,
                    String.valueOf(memberId), member.getUsername(), null);
        }
        log.info("部门成员绑定完成：departmentCode={}, memberCount={}", department.getCode(), memberIds.size());
    }

    /**
     * 更新部门
     */
    @Transactional
    public IamDepartmentEntity updateDepartment(Long departmentId, UpdateDepartmentRequest request) {
        IamDepartmentEntity department = getById(departmentId);
        validateParent(request.getParentId(), departmentId);
        department.setName(normalizeRequired(request.getName(), ServerErrorMessage.DEPARTMENT_NAME_EMPTY));
        department.setParentId(request.getParentId());
        department.setSortOrder(resolveSortOrder(request.getSortOrder()));
        department.setStatus(resolveStatus(request.getStatus()));
        department.setUpdatedAt(Instant.now());
        IamDepartmentEntity saved = departmentRepository.save(department);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.DEPARTMENT,
                String.valueOf(departmentId), saved.getCode(), null);
        return saved;
    }

    /**
     * 删除部门
     */
    @Transactional
    public void deleteDepartment(Long departmentId) {
        IamDepartmentEntity department = getById(departmentId);
        if (departmentRepository.existsByParentId(departmentId)) {
            throw new SimpleIamServerException(ErrorCode.DEPARTMENT_DELETE_BLOCKED,
                    String.format(ServerErrorMessage.DEPARTMENT_HAS_CHILD, department.getCode()));
        }
        if (userRepository.existsByDepartmentId(departmentId)) {
            throw new SimpleIamServerException(ErrorCode.DEPARTMENT_DELETE_BLOCKED,
                    String.format(ServerErrorMessage.DEPARTMENT_HAS_USER, department.getCode()));
        }
        departmentRepository.delete(department);
        log.info("部门删除成功：code={}, id={}", department.getCode(), departmentId);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.DEPARTMENT,
                String.valueOf(departmentId), department.getCode(), null);
    }

    /**
     * 根据 ID 查询部门，不存在则抛异常
     */
    public IamDepartmentEntity getById(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        String.format(ServerErrorMessage.DEPARTMENT_NOT_FOUND, departmentId)));
    }

    /**
     * 查询部门名称
     */
    public String getDepartmentName(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId).map(IamDepartmentEntity::getName).orElse(null);
    }

    /**
     * 解析部门和子部门 ID
     */
    public List<Long> resolveDepartmentIds(Collection<Long> departmentIds, boolean includeChildDepartments) {
        return resolveDepartmentIds(departmentIds, includeChildDepartments, false);
    }

    /**
     * 解析启用部门和启用子部门 ID，用于发送站内信
     */
    public List<Long> resolveActiveDepartmentIds(Collection<Long> departmentIds, boolean includeChildDepartments) {
        return resolveDepartmentIds(departmentIds, includeChildDepartments, true);
    }

    private List<Long> resolveDepartmentIds(Collection<Long> departmentIds,
                                            boolean includeChildDepartments,
                                            boolean activeOnly) {
        List<Long> normalizedIds = normalizeIds(departmentIds);
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> resolvedIds = new LinkedHashSet<Long>();
        Queue<Long> queue = new LinkedList<Long>();
        for (Long departmentId : normalizedIds) {
            IamDepartmentEntity department = getById(departmentId);
            if (!activeOnly || SimpleIamServerConstant.STATUS_ACTIVE == department.getStatus()) {
                resolvedIds.add(departmentId);
                queue.add(departmentId);
            }
        }
        if (!includeChildDepartments) {
            return new ArrayList<Long>(resolvedIds);
        }
        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            List<IamDepartmentEntity> children = activeOnly
                    ? departmentRepository.findByParentIdAndStatus(parentId, SimpleIamServerConstant.STATUS_ACTIVE)
                    : departmentRepository.findByParentId(parentId);
            for (IamDepartmentEntity child : children) {
                if (resolvedIds.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return new ArrayList<Long>(resolvedIds);
    }

    private void validateParent(Long parentId, Long currentDepartmentId) {
        if (parentId == null) {
            return;
        }
        getById(parentId);
        if (parentId.equals(currentDepartmentId)) {
            throw new SimpleIamServerException(ErrorCode.DEPARTMENT_PARENT_INVALID,
                    String.format(ServerErrorMessage.DEPARTMENT_PARENT_INVALID, parentId));
        }
        if (currentDepartmentId != null && resolveDepartmentIds(Collections.singletonList(currentDepartmentId), true).contains(parentId)) {
            throw new SimpleIamServerException(ErrorCode.DEPARTMENT_PARENT_INVALID,
                    String.format(ServerErrorMessage.DEPARTMENT_PARENT_INVALID, parentId));
        }
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

    private int resolveSortOrder(Integer sortOrder) {
        return sortOrder == null ? SimpleIamServerConstant.DEFAULT_SORT_ORDER : sortOrder;
    }

    private int resolveStatus(Integer status) {
        return status == null ? SimpleIamServerConstant.STATUS_ACTIVE : status;
    }
}
