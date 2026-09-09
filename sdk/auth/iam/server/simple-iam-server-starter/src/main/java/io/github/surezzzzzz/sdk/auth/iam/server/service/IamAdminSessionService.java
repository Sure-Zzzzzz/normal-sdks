package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response.AdminSessionResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 管理台会话管理服务
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAdminSessionService {

    private final IamSessionRepository sessionRepository;
    private final UserService userService;
    private final SessionService sessionService;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 分页查询活跃会话（status=1 且未过期），按最近活跃倒序；userId 为 null 时查全部
     */
    public Page<AdminSessionResponse> listActiveSessions(Long userId, int page, int size) {
        int safePage = Math.max(page, 1) - 1;
        int safeSize = Math.min(Math.max(size, 1), SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return sessionRepository.searchActiveSessions(userId, Instant.now(), pageable)
                .map(AdminSessionResponse::from);
    }

    /**
     * 强制下线用户的全部会话（Redis + OAuth2 授权 + MySQL 一并撤销，SessionService 内发会话审计事件）
     *
     * @return 实际撤销的会话数
     */
    @Transactional
    public int revokeUserSessions(Long userId) {
        IamUserEntity user = userService.getById(userId);
        int revoked = sessionService.revokeAllByUserId(userId);
        log.info("管理员强制下线：username={}, userId={}, revoked={}", user.getUsername(), userId, revoked);
        auditEventPublisher.publishAdminAction(
                AdminActionType.REVOKED,
                AdminSubjectType.USER,
                String.valueOf(userId), user.getUsername(), "revokedSessions=" + revoked);
        return revoked;
    }
}
