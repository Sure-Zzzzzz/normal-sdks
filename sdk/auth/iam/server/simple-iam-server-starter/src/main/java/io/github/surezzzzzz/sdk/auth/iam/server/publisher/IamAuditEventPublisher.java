package io.github.surezzzzzz.sdk.auth.iam.server.publisher;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * IAM 审计事件统一发布门面。
 *
 * <p>发布失败只记告警，不影响任何主流程；管理面事件的 {@code operator} 在发布线程
 * 内同步解析当前认证用户（消费侧 AFTER_COMMIT 异步时 SecurityContext 已不可用），
 * 系统自动操作（bootstrap、部署恢复）解析不到时为 null。
 *
 * <p>Token 族事件（颁发 / 撤销 / 删除 / 验证终审 / 复用检测）由各自发布点直接构造，
 * 不经本门面。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAuditEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发布认证事件（登录成败 / 登出 / 账号锁定）。
     */
    public void publishAuthentication(AuthenticationEventType eventType,
                                      String provider, String username, Long userId,
                                      String ip, String userAgent, String errorCode,
                                      Integer failureCount) {
        publish(new AuthenticationEvent(this, eventType, provider, username, userId,
                ip, userAgent, errorCode, failureCount));
    }

    /**
     * 发布会话生命周期事件（建立 / 撤销）。
     */
    public void publishSession(SessionEventType eventType, SessionEventCause cause,
                               String sessionId, Long userId, String username,
                               Integer revokedCount) {
        publish(new SessionLifecycleEvent(this, eventType, cause, sessionId,
                userId, username, revokedCount));
    }

    /**
     * 发布管理面操作事件；operator 自动解析当前认证用户。
     */
    public void publishAdminAction(AdminActionType action, AdminSubjectType subjectType,
                                   String subjectId, String subjectName, String detail) {
        publish(new AdminActionEvent(this, action, subjectType, subjectId, subjectName,
                resolveOperator(), detail));
    }

    private void publish(AbstractIamEvent event) {
        try {
            eventPublisher.publishEvent(event);
            log.debug("Published IAM audit event: {}", event.getClass().getSimpleName());
        } catch (Exception exception) {
            log.warn("Failed to publish IAM audit event: {}", event.getClass().getSimpleName(), exception);
        }
    }

    /**
     * 解析当前操作人；匿名或未认证（系统自动操作）返回 null。
     *
     * <p>开放 API 调用主体（公共资源层 {@code VerifiedResourceAuthentication}，principal 为
     * {@code VerifiedResourceContext}）未覆写 getName，直接取名会得到整段 toString；
     * 特判后 operator 为 {@code <sourceId>:<subjectId>}（如 {@code aksk:crm-sync}），
     * 与 ResourceAccessEvent 的 subjectId 同源，访问级与领域级审计可串查。
     */
    private String resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof VerifiedResourceContext) {
            VerifiedResourcePrincipal resourcePrincipal =
                    ((VerifiedResourceContext) principal).getPrincipal();
            return resourcePrincipal.getSourceId().getValue() + ":" + resourcePrincipal.getSubjectId();
        }
        String name = authentication.getName();
        return "anonymousUser".equals(name) ? null : name;
    }
}
