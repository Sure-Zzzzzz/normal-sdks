package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.SessionEventCause;
import io.github.surezzzzzz.sdk.auth.iam.server.event.SessionEventType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamSessionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 会话服务
 *
 * <p>会话建立与撤销均发布 {@link io.github.surezzzzzz.sdk.auth.iam.server.event.SessionLifecycleEvent}；
 * 撤销来源经 {@link SessionEventCause} 区分（登出 / 重登踢旧 / 用户生命周期批量吊销）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class SessionService {

    private final IamSessionRepository sessionRepository;
    private final RedisTokenRepository redisTokenRepository;
    private final IamAuthorizationRevocationSupport authorizationRevocationSupport;
    private final SimpleIamServerProperties properties;
    private final IamAuditEventPublisher auditEventPublisher;
    /**
     * 延迟解析打断构造环：MessageSseService 懒校验依赖本类，
     * 用户级吊销又需反向清理其 SSE 连接（先例 IamAuthorizationRevocationSupport）
     */
    private final ObjectProvider<MessageSseService> messageSseServiceProvider;

    /**
     * 双时钟配置校验：绝对上限必须大于滑动空闲超时（否则两钟互相抵消，
     * 只剩绝对上限生效），节流窗必须为正——违反即启动失败。
     * public 供测试直调校验逻辑
     */
    @PostConstruct
    public void validateSessionClock() {
        SimpleIamServerProperties.SessionConfig config = properties.getSession();
        if (config.getAbsoluteExpiresIn() <= config.getExpiresIn() || config.getRenewThreshold() <= 0) {
            throw new ConfigurationException(String.format(
                    "session 配置非法：absolute-expires-in(%d) 须大于 expires-in(%d) 且 renew-threshold(%d) 须为正",
                    config.getAbsoluteExpiresIn(), config.getExpiresIn(), config.getRenewThreshold()));
        }
    }

    /**
     * 创建会话
     *
     * @param userId    用户 ID
     * @param username  用户名
     * @param clientId  客户端 ID
     * @param remoteIp  远端 IP
     * @param userAgent User-Agent
     * @return 会话实体
     */
    @Transactional
    public IamSessionEntity createSession(Long userId, String username, String clientId,
                                          String remoteIp, String userAgent) {
        return saveSession(newSession(userId, username, clientId, remoteIp, userAgent));
    }

    /**
     * 创建并绑定 Servlet 会话的 IAM 会话
     *
     * @param userId           用户 ID
     * @param username         用户名
     * @param clientId         客户端 ID
     * @param servletSessionId Servlet 会话 ID
     * @param remoteIp         远端 IP
     * @param userAgent        User-Agent
     * @return 会话实体
     */
    @Transactional
    public IamSessionEntity createBoundSession(Long userId, String username, String clientId,
                                               String servletSessionId, String remoteIp, String userAgent) {
        IamSessionEntity session = newSession(userId, username, clientId, remoteIp, userAgent);
        Instant now = Instant.now();
        session.setServletSessionIdHash(TokenHashHelper.sha256Hex(servletSessionId));
        session.setAuthTime(now);
        session.setLastActiveAt(now);
        session.setMfaLevel(0);
        return saveSession(session);
    }

    private IamSessionEntity newSession(Long userId, String username, String clientId,
                                        String remoteIp, String userAgent) {
        Instant now = Instant.now();
        IamSessionEntity session = new IamSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setUsername(username);
        session.setIssuer(properties.getIssuer());
        session.setClientId(clientId);
        session.setRemoteIp(remoteIp);
        session.setUserAgent(userAgent);
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusSeconds(properties.getSession().getExpiresIn()));
        session.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        return session;
    }

    private IamSessionEntity saveSession(IamSessionEntity session) {
        IamSessionEntity saved = sessionRepository.save(session);
        redisTokenRepository.saveSession(saved.getId(), saved,
                Duration.ofSeconds(properties.getSession().getExpiresIn()));
        auditEventPublisher.publishSession(SessionEventType.CREATED, null,
                saved.getId(), saved.getUserId(), saved.getUsername(), null);
        return saved;
    }

    /**
     * 查询 Servlet 会话绑定的活跃 IAM 会话
     *
     * @param servletSessionId Servlet 会话 ID
     * @return IAM 会话，不存在时返回 null
     */
    public IamSessionEntity findActiveByServletSessionId(String servletSessionId) {
        return findActiveByServletSessionIdHash(TokenHashHelper.sha256Hex(servletSessionId));
    }

    /**
     * 按 Servlet 会话 ID 哈希查询活跃 IAM 会话（SSE 懒校验按哈希直查，
     * 与登录态校验过滤器同判据同数据源）
     *
     * @param servletSessionIdHash Servlet 会话 ID 的 SHA-256 哈希
     * @return IAM 会话，不存在时返回 null
     */
    public IamSessionEntity findActiveByServletSessionIdHash(String servletSessionIdHash) {
        List<IamSessionEntity> sessions = sessionRepository.findByServletSessionIdHashAndStatusOrderByIssuedAtDesc(
                servletSessionIdHash, SimpleIamServerConstant.STATUS_ACTIVE);
        return sessions.stream()
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 活跃请求滑动续期（双时钟）：
     * {@code newExpiresAt = min(now + expiresIn, issuedAt + absoluteExpiresIn)}。
     *
     * <p>节流：距上次续期未超过 renewThreshold 或新有效期未超过当前值（已续到
     * 绝对上限天花板）时零写；落库走定向 UPDATE（status=1 守卫，已撤销行不可
     * 覆盖复活），成功后失效 Redis 热数据缓存（失效优于更新：无并发撤销后
     * 竞态写回旧值的窗口），缓存失效失败仅告警不阻断。</p>
     *
     * @param session 登录态校验通过的会话实体（校验链路刚查出，非持久化上下文托管对象）
     */
    @Transactional
    public void touchIfNeeded(IamSessionEntity session) {
        Instant now = Instant.now();
        Instant lastActive = session.getLastActiveAt() != null ? session.getLastActiveAt() : session.getIssuedAt();
        Instant ceiling = session.getIssuedAt()
                .plusSeconds(properties.getSession().getAbsoluteExpiresIn());
        Instant newExpiresAt = now.plusSeconds(properties.getSession().getExpiresIn());
        if (ceiling.isBefore(newExpiresAt)) {
            newExpiresAt = ceiling;
        }
        if (!session.getExpiresAt().isBefore(newExpiresAt)
                || Duration.between(lastActive, now).getSeconds() <= properties.getSession().getRenewThreshold()) {
            return;
        }
        int updated = sessionRepository.touchSession(session.getId(), now, newExpiresAt);
        if (updated == 0) {
            log.debug("会话续期未命中（已撤销或不存在），跳过：sessionId={}", session.getId());
            return;
        }
        session.setLastActiveAt(now);
        session.setExpiresAt(newExpiresAt);
        try {
            redisTokenRepository.deleteSession(session.getId());
        } catch (Exception exception) {
            log.warn("会话续期后失效 Redis 热数据失败，等待 TTL 自然消亡：sessionId={}",
                    session.getId(), exception);
        }
        log.debug("会话滑动续期：sessionId={}, newExpiresAt={}", session.getId(), newExpiresAt);
    }

    /**
     * 校验 Servlet 会话与 IAM 会话绑定是否仍然有效
     *
     * @param servletSession Servlet 会话
     * @return 绑定存在、活跃且哈希匹配时返回 true
     */
    public boolean isActiveBinding(javax.servlet.http.HttpSession servletSession) {
        return getActiveBinding(servletSession) != null;
    }

    /**
     * 获取 Servlet 会话绑定的活跃 IAM 会话
     *
     * @param servletSession Servlet 会话
     * @return 绑定存在、活跃且哈希匹配时返回 IAM 会话，否则返回 null
     */
    public IamSessionEntity getActiveBinding(javax.servlet.http.HttpSession servletSession) {
        Object iamSessionId = servletSession.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_IAM_SESSION_ID);
        if (!(iamSessionId instanceof String)) {
            return null;
        }
        IamSessionEntity iamSession = findActiveByServletSessionId(servletSession.getId());
        return iamSession != null && iamSessionId.equals(iamSession.getId()) ? iamSession : null;
    }

    /**
     * 查询会话，优先读 Redis 热数据
     *
     * @param sessionId 会话 ID
     * @return 会话实体
     */
    public IamSessionEntity getSession(String sessionId) {
        IamSessionEntity cached = redisTokenRepository.getSession(sessionId);
        if (cached != null) {
            return cached;
        }
        IamSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.SESSION_NOT_FOUND, "会话不存在：" + sessionId));
        if (session.getExpiresAt().isBefore(Instant.now())) {
            throw new SimpleIamServerException(ErrorCode.SESSION_EXPIRED, "会话已过期：" + sessionId);
        }
        return session;
    }

    /**
     * 查询有效 IAM 会话
     *
     * @param sessionId IAM 会话 ID
     * @return 活跃且未过期的 IAM 会话，不存在或不可用时返回 null
     */
    public IamSessionEntity findActiveById(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        IamSessionEntity session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SimpleIamServerConstant.STATUS_ACTIVE
                || !session.getExpiresAt().isAfter(Instant.now())) {
            return null;
        }
        return session;
    }

    /**
     * 查询用户活跃会话
     *
     * @param userId 用户 ID
     * @return 活跃会话列表
     */
    public List<IamSessionEntity> getActiveSessions(Long userId) {
        return sessionRepository.findByUserIdAndStatus(userId, SimpleIamServerConstant.STATUS_ACTIVE);
    }

    /**
     * 撤销 Servlet 会话当前绑定的 IAM 会话
     *
     * @param servletSessionId Servlet 会话 ID
     */
    @Transactional
    public void revokeActiveByServletSessionId(String servletSessionId) {
        List<IamSessionEntity> sessions = sessionRepository.findByServletSessionIdHashAndStatusOrderByIssuedAtDesc(
                TokenHashHelper.sha256Hex(servletSessionId), SimpleIamServerConstant.STATUS_ACTIVE);
        for (IamSessionEntity session : sessions) {
            revokeSession(session.getId(), SessionEventCause.RELOGIN_KICK);
        }
    }

    /**
     * 撤销会话，并联动清理该 Servlet 会话关联的 OAuth2 授权（access/code 物理删除）。
     * 撤销来源归为主动登出。
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void revokeSession(String sessionId) {
        revokeSession(sessionId, SessionEventCause.LOGOUT);
    }

    /**
     * 撤销会话并携带撤销来源（登出 / 重登踢旧），供审计事件区分触发链路。
     *
     * @param sessionId 会话 ID
     * @param cause     撤销来源
     */
    @Transactional
    public void revokeSession(String sessionId, SessionEventCause cause) {
        IamSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.SESSION_NOT_FOUND, "会话不存在：" + sessionId));
        session.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);
        redisTokenRepository.deleteSession(sessionId);
        authorizationRevocationSupport.revokeByServletSessionIdHash(session.getServletSessionIdHash());
        auditEventPublisher.publishSession(SessionEventType.REVOKED, cause,
                session.getId(), session.getUserId(), session.getUsername(), null);
    }

    /**
     * 撤销用户全部活跃会话
     *
     * <p>同时联动清理该用户全部 OAuth2 授权（access/code 物理删除），
     * 覆盖禁用 / 删除 / 重置密码等批量吊销路径。批量撤销发布单个事件携带数量。</p>
     *
     * @param userId 用户 ID
     * @return 撤销数量
     */
    @Transactional
    public int revokeAllByUserId(Long userId) {
        for (IamSessionEntity session : getActiveSessions(userId)) {
            redisTokenRepository.deleteSession(session.getId());
        }
        authorizationRevocationSupport.revokeByUserId(userId);
        int revoked = sessionRepository.revokeAllByUserId(userId, Instant.now());
        auditEventPublisher.publishSession(SessionEventType.REVOKED, SessionEventCause.USER_LIFECYCLE,
                null, userId, null, revoked);
        // 用户级吊销语义上该用户全部端即时下线；事务内尽力踢，回滚窗口由前端重连自愈
        MessageSseService sseService = messageSseServiceProvider.getIfAvailable();
        if (sseService != null) {
            sseService.evictUserEmitters(userId);
        }
        return revoked;
    }

    /**
     * 撤销用户除指定会话外的全部活跃会话（自助改密路径：踢掉其他端，保住当前会话）。
     *
     * <p>OAuth2 授权按用户全量清理（含当前会话签发的 token——旧密码签发的
     * token 一律失效，改密后的应用接入重新走授权码流程；IAM 自身登录态走
     * 会话不受影响）。用户级全量兜底无会话绑定的授权行；SSE 按用户踢出，
     * 当前端断线自动重连。</p>
     *
     * @param userId          用户 ID
     * @param exceptSessionId 保留的 IAM 会话 ID（当前登录会话）
     * @return 撤销数量
     */
    @Transactional
    public int revokeAllByUserIdExcept(Long userId, String exceptSessionId) {
        authorizationRevocationSupport.revokeByUserId(userId);
        int revoked = 0;
        for (IamSessionEntity session : getActiveSessions(userId)) {
            if (session.getId().equals(exceptSessionId)) {
                continue;
            }
            revokeSession(session.getId(), SessionEventCause.USER_LIFECYCLE);
            revoked++;
        }
        MessageSseService sseService = messageSseServiceProvider.getIfAvailable();
        if (sseService != null) {
            sseService.evictUserEmitters(userId);
        }
        return revoked;
    }

    /**
     * 清理过期会话
     *
     * @return 清理数量
     */
    @Transactional
    public int cleanupExpiredSessions() {
        List<IamSessionEntity> expired = sessionRepository.findByExpiresAtBeforeAndStatus(Instant.now(), SimpleIamServerConstant.STATUS_ACTIVE);
        for (IamSessionEntity session : expired) {
            session.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
            session.setRevokedAt(Instant.now());
            redisTokenRepository.deleteSession(session.getId());
        }
        sessionRepository.saveAll(expired);
        return expired.size();
    }
}
