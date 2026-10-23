package io.github.surezzzzzz.sdk.iam.gateway.adaptor.starter.service;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model.RouteSnapshot;

/**
 * adaptor 内部的 xDS 发布端口；starter 将快照提交到自身 ADS/SotW 控制面。
 */
public interface XdsSnapshotPort {

    /**
     * 提交完整快照，返回是否已交给 adaptor 控制面缓存。
     */
    boolean publish(RouteSnapshot snapshot);
}
