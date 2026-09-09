package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.model.PendingAuthorization;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 授权暂存上下文的实例内内存实现（单实例部署 / 宿主无 redis-route 时的兜底）。
 *
 * <p>过期上下文在下次 save 时顺带清理；consume 对过期项按不存在处理。</p>
 *
 * @author surezzzzzz
 */
public class MemoryPendingStateStore implements PendingStateStore {

    private final ConcurrentMap<String, MemoryEntry> pendingByState = new ConcurrentHashMap<>();

    /**
     * 暂存 state → 授权上下文（单实例内存态，写入前清扫过期项）
     */
    @Override
    public void save(String state, PendingAuthorization pending, Duration ttl) {
        purgeExpired();
        pendingByState.put(state, new MemoryEntry(pending,
                System.currentTimeMillis() + ttl.toMillis()));
    }

    /**
     * 一次性消费 state（过期或不存在返回 null）
     */
    @Override
    public PendingAuthorization consume(String state) {
        MemoryEntry entry = pendingByState.remove(state);
        if (entry == null || entry.expiresAt < System.currentTimeMillis()) {
            return null;
        }
        return entry.pending;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        pendingByState.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
    }

    private static final class MemoryEntry {

        private final PendingAuthorization pending;

        private final long expiresAt;

        private MemoryEntry(PendingAuthorization pending, long expiresAt) {
            this.pending = pending;
            this.expiresAt = expiresAt;
        }
    }
}
