package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.UpdateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import io.github.surezzzzzz.sdk.auth.iam.server.validator.PasswordPolicyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;

/**
 * 用户管理服务
 *
 * <p>用户生命周期写操作（创建 / 更新 / 删除 / 启禁用 / 重置密码）均发布管理面审计事件。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class UserService {

    private final IamUserRepository userRepository;
    private final IamUserRoleRepository userRoleRepository;
    private final IamUserGroupMemberRepository userGroupMemberRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final DepartmentService departmentService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final IamAuditEventPublisher auditEventPublisher;
    private final RoleService roleService;
    private final IamAuthorizationProjectionService projectionService;
    private final RedisTokenRepository redisTokenRepository;
    private final LoginFailurePolicySupport loginFailurePolicySupport;

    /**
     * 创建用户
     */
    @Transactional
    public IamUserEntity createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new SimpleIamServerException(
                    String.format(ServerErrorMessage.USER_ALREADY_EXISTS, request.getUsername()));
        }
        passwordPolicyValidator.validate(request.getPassword());
        validateDepartment(request.getDepartmentId());

        IamUserEntity user = new IamUserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setDepartmentId(request.getDepartmentId());
        user.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setMustChangePassword(Boolean.TRUE);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        IamUserEntity saved = userRepository.save(user);
        log.info("用户创建成功：username={}, id={}", saved.getUsername(), saved.getId());
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.USER,
                String.valueOf(saved.getId()), saved.getUsername(), null);
        return saved;
    }

    /**
     * 更新用户信息（displayName / email / phone / departmentId）
     *
     * <p>departmentId 实际发生变化时（转入新部门或清空部门），触发权限版本号 bump
     * 与授权投影重算，让在线会话及时感知新部门的有效角色变化；若该用户当前是
     * 系统唯一有效管理员且转部门会导致其失去 iam_admin，则拒绝本次转部门。
     */
    @Transactional
    public IamUserEntity updateUser(Long userId, UpdateUserRequest request) {
        IamUserEntity user = getById(userId);
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        boolean departmentChanged = false;
        if (request.getDepartmentId() != null) {
            validateDepartment(request.getDepartmentId());
            if (!request.getDepartmentId().equals(user.getDepartmentId())) {
                roleService.assertDepartmentTransferNotLastActiveAdmin(userId, request.getDepartmentId());
                departmentChanged = true;
            }
            user.setDepartmentId(request.getDepartmentId());
        } else if (Boolean.TRUE.equals(request.getClearDepartment())) {
            if (user.getDepartmentId() != null) {
                roleService.assertDepartmentTransferNotLastActiveAdmin(userId, null);
                departmentChanged = true;
            }
            user.setDepartmentId(null);
        }
        user.setUpdatedAt(Instant.now());
        IamUserEntity saved = userRepository.save(user);
        if (departmentChanged) {
            userRepository.bumpPermissionVersion(Collections.singletonList(userId));
            projectionService.onUserRoleAssigned(userId);
        }
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.USER,
                String.valueOf(userId), saved.getUsername(), null);
        return saved;
    }

    /**
     * 管理台分页查询用户（lastLoginAfter/lockedUntilAfter/noDepartment 为仪表盘下钻筛选：今日登录、锁定中、未挂部门）
     */
    public Page<IamUserEntity> listUsers(Integer status, Long departmentId, String keyword,
                                         Instant lastLoginAfter, Instant lockedUntilAfter, boolean noDepartment,
                                         int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        return userRepository.searchForConsole(status, departmentId, normalizedKeyword, lastLoginAfter,
                lockedUntilAfter, noDepartment, pageable);
    }

    /**
     * 开放 API 分页查询用户：departmentScope 为 DATA 受限部门范围（与请求条件求交，越权数据不出库），
     * null = 全量（走无 DATA 过滤的查询——受限查询的 {@code IN :departmentScope} 绑定 null 集合
     * 会恒空匹配，全量不能复用）；空集 = 无可见部门（直接空页，不查库）
     */
    public Page<IamUserEntity> listUsers(Integer status, Long departmentId, String keyword,
                                         java.util.Collection<Long> departmentScope, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (departmentScope != null && departmentScope.isEmpty()) {
            return Page.empty(pageable);
        }
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();
        if (departmentScope == null) {
            return userRepository.searchForConsole(status, departmentId, normalizedKeyword, null, null, false, pageable);
        }
        return userRepository.searchForRestApi(status, departmentId, normalizedKeyword, departmentScope, pageable);
    }

    /**
     * 删除用户（物理删除；先吊销其全部会话，级联删除角色绑定、组成员与应用授权投影）；
     * 最后一个可用管理员不可删除
     */
    @Transactional
    public void deleteUser(Long userId) {
        roleService.assertNotLastActiveAdmin(userId);
        IamUserEntity user = getById(userId);
        sessionService.revokeAllByUserId(userId);
        userRoleRepository.deleteByUserId(userId);
        userGroupMemberRepository.deleteByUserId(userId);
        applicationAuthorizationRepository.deleteByUserId(userId);
        userRepository.delete(user);
        log.info("用户删除成功：username={}, id={}", user.getUsername(), userId);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), null);
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enableUser(Long userId) {
        IamUserEntity user = getById(userId);
        user.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        log.info("用户启用成功：username={}, id={}", user.getUsername(), userId);
        auditEventPublisher.publishAdminAction(AdminActionType.ENABLED, AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), null);
    }

    /**
     * 禁用用户（立即吊销其全部会话）；最后一个可用管理员不可禁用
     */
    @Transactional
    public void disableUser(Long userId) {
        roleService.assertNotLastActiveAdmin(userId);
        IamUserEntity user = getById(userId);
        user.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        sessionService.revokeAllByUserId(userId);
        log.info("用户禁用成功：username={}, id={}, 已吊销会话", user.getUsername(), userId);
        auditEventPublisher.publishAdminAction(AdminActionType.DISABLED, AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), null);
    }

    /**
     * 手动解锁（清除登录失败锁定与 Redis 失败计数，立即生效；不改变账号启禁用状态）
     */
    @Transactional
    public void unlockUser(Long userId) {
        IamUserEntity user = getById(userId);
        user.setLockedUntil(null);
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        redisTokenRepository.deleteLoginFailure(user.getUsername());
        log.info("用户手动解锁成功：username={}, id={}, 已清除失败计数", user.getUsername(), userId);
        auditEventPublisher.publishAdminAction(AdminActionType.UNLOCKED, AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), null);
    }

    /**
     * 重置密码（管理员或自助；立即吊销该用户全部会话，置须改密标记——
     * 重置发出的都是临时密码，下次登录强制修改）。
     * 外部身份源账号无本地密码，重置无意义且会将其锁进无法完成的改密流程，直接拒绝。
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword, String requestedBy) {
        IamUserEntity user = getById(userId);
        if (StringUtils.hasText(user.getIdentitySource())) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED,
                    ServerErrorMessage.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        passwordPolicyValidator.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setLockedUntil(null);
        user.setMustChangePassword(Boolean.TRUE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        redisTokenRepository.deleteLoginFailure(user.getUsername());
        sessionService.revokeAllByUserId(userId);
        log.info("密码重置成功：username={}, requestedBy={}", user.getUsername(), requestedBy);
        auditEventPublisher.publishAdminAction(AdminActionType.PASSWORD_RESET, AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), requestedBy);
    }

    /**
     * 用户自助修改密码（须改密标记强制路径与主动改密共用）。
     *
     * <p>仅本地密码用户可改（外部身份源账号密码由外部系统管理）；旧密码校验
     * 失败与登录同口径计入失败计数并按上限锁定；成功后清须改密标记、踢掉
     * 其他端会话并吊销全部 OAuth2 授权与 refresh 族（旧密码签发的 token
     * 一律失效），当前会话保留。</p>
     *
     * @param userId           用户 ID
     * @param oldPassword      原密码
     * @param newPassword      新密码
     * @param currentSessionId 当前 IAM 会话 ID（改密后保留，null 视为全部踢除）
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword, String currentSessionId) {
        IamUserEntity user = getById(userId);
        loginFailurePolicySupport.assertAccountUsable(user);
        if (StringUtils.hasText(user.getIdentitySource())) {
            throw new SimpleIamServerException(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED,
                    ServerErrorMessage.PASSWORD_CHANGE_NOT_ALLOWED);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            long failures = loginFailurePolicySupport.recordLocalFailure(user);
            throw new SimpleIamServerException(ErrorCode.BAD_CREDENTIALS, String.format(
                    ServerErrorMessage.BAD_CREDENTIALS_REMAINING,
                    loginFailurePolicySupport.remainingAttempts(failures)));
        }
        passwordPolicyValidator.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginCount(SimpleIamServerConstant.DEFAULT_FAILED_LOGIN_COUNT);
        user.setLockedUntil(null);
        user.setMustChangePassword(Boolean.FALSE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        redisTokenRepository.deleteLoginFailure(user.getUsername());
        if (currentSessionId == null) {
            sessionService.revokeAllByUserId(userId);
        } else {
            sessionService.revokeAllByUserIdExcept(userId, currentSessionId);
        }
        log.info("用户自助改密成功：username={}", user.getUsername());
        auditEventPublisher.publishAdminAction(AdminActionType.PASSWORD_CHANGED, AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), user.getUsername());
    }

    /**
     * 根据 ID 查询用户，不存在则抛异常
     */
    public IamUserEntity getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new SimpleIamServerException(
                        String.format(ServerErrorMessage.USER_NOT_FOUND, userId)));
    }

    /**
     * 根据用户名查询用户，不存在则抛异常
     */
    public IamUserEntity getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new SimpleIamServerException(
                        String.format(ServerErrorMessage.USER_NOT_FOUND, username)));
    }

    private void validateDepartment(Long departmentId) {
        if (departmentId != null) {
            departmentService.getById(departmentId);
        }
    }
}
