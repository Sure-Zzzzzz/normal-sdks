package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Getter;

/**
 * Gateway 控制面事件类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum GatewayRouteEventType {
    /**
     * 开始读取目录。
     */
    CATALOG_SYNC_STARTED("catalog-sync-started"),
    /**
     * 目录或目标被拒绝。
     */
    ROUTE_REJECTED("route-rejected"),
    /**
     * 快照已交给当前控制面端口，不代表 Envoy 已确认应用。
     */
    SNAPSHOT_PUBLISHED("snapshot-published"),
    /**
     * 新快照已完成控制面确认。
     */
    SNAPSHOT_APPLIED("snapshot-applied"),
    /**
     * 依赖故障时保留上一份有效快照。
     */
    SNAPSHOT_RETAINED("snapshot-retained"),
    /**
     * 旧快照超过允许窗口。
     */
    SNAPSHOT_EXPIRED("snapshot-expired"),
    /**
     * xDS ACK。
     */
    XDS_ACK("xds-ack"),
    /**
     * xDS NACK。
     */
    XDS_NACK("xds-nack");

    private final String code;

    GatewayRouteEventType(String code) {
        this.code = code;
    }
}
