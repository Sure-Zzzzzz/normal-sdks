package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.annotation.SimpleIamPortalGatewayAdaptorComponent;
import io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.configuration.SimpleIamPortalGatewayAdaptorProperties;

/**
 * 提供 runtime 可映射为 HTTP health/readiness 的无状态查询端口。
 */
@SimpleIamPortalGatewayAdaptorComponent
public class GatewayHealthEndpoint {

    private final GatewayRouteSnapshotState state;
    private final SimpleIamPortalGatewayAdaptorProperties properties;
    private final XdsSnapshotService xds;

    public GatewayHealthEndpoint(GatewayRouteSnapshotState state,
                                 SimpleIamPortalGatewayAdaptorProperties properties,
                                 XdsSnapshotService xds) {
        this.state = state;
        this.properties = properties;
        this.xds = xds;
    }

    /**
     * 进程存活与否不依赖 IAM。
     */
    public boolean isAlive() {
        return true;
    }

    /**
     * 只有有效快照且未超过 maxStale 才接受新流量。
     */
    public boolean isReady() {
        if (!xds.isRunning()) {
            return false;
        }
        RouteSnapshot current = state.current();
        if (current == null) {
            return false;
        }
        if (state.failureReason() == null) {
            return xds.hasAppliedLatestSnapshot();
        }
        long maxStale = properties.getMaxStaleMillis();
        return maxStale > 0L && System.currentTimeMillis() - state.lastAppliedAt() <= maxStale;
    }

    public RouteSnapshot currentSnapshot() {
        return state.current();
    }

    public String failureReason() {
        return state.failureReason();
    }
}
