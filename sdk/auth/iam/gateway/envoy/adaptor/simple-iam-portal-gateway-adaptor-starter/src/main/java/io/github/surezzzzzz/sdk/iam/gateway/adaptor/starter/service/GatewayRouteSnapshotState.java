package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 内存中的整份快照和依赖状态；副本重启后必须重新从 IAM 读取。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class GatewayRouteSnapshotState {

    private final AtomicReference<RouteSnapshot> snapshot = new AtomicReference<>();
    private final AtomicReference<String> failureReason = new AtomicReference<>();
    private final AtomicLong lastAppliedAt = new AtomicLong();
    private final AtomicLong lastFailureAt = new AtomicLong();

    /**
     * 原子替换完整快照，避免读者看到半份路由。
     */
    public void replace(RouteSnapshot newSnapshot) {
        snapshot.set(newSnapshot);
        failureReason.set(null);
        lastAppliedAt.set(System.currentTimeMillis());
    }

    /**
     * 记录依赖失败，不修改旧快照。
     */
    public void recordFailure(String reason) {
        failureReason.set(reason);
        lastFailureAt.set(System.currentTimeMillis());
    }

    public RouteSnapshot current() {
        return snapshot.get();
    }

    public String failureReason() {
        return failureReason.get();
    }

    public long lastAppliedAt() {
        return lastAppliedAt.get();
    }

    public long lastFailureAt() {
        return lastFailureAt.get();
    }
}
