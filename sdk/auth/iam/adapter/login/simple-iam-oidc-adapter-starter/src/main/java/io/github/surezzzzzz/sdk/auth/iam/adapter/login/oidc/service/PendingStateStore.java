package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.model.PendingAuthorization;

import java.time.Duration;

/**
 * OIDC 授权暂存上下文的存取契约：authorize 阶段以 state 为键保存，callback 阶段读取即删除。
 *
 * <p>适配器不持有浏览器会话（SPI 无状态契约），state 是 authorize 与 callback
 * 两次调用间唯一的关联物。多实例部署下两次调用可能落在不同实例，
 * {@link RedisPendingStateStore} 以共享 Redis 承载；单实例或无 route 宿主
 * 由 {@link MemoryPendingStateStore} 兜底。</p>
 *
 * @author surezzzzzz
 */
public interface PendingStateStore {

    /**
     * 保存授权上下文，ttl 后自动不可见（内存实现按过期时间剔除，Redis 实现由 TTL 接管）
     *
     * @param state   授权 state
     * @param pending 授权上下文
     * @param ttl     保留时长
     */
    void save(String state, PendingAuthorization pending, Duration ttl);

    /**
     * 读取并删除授权上下文（原子，state 一次性）
     *
     * @param state 授权 state
     * @return 授权上下文，state 不存在或已过期/已使用时返回 null
     */
    PendingAuthorization consume(String state);
}
