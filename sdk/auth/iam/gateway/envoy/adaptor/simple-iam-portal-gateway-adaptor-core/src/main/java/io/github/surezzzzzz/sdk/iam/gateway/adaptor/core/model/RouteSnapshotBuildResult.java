package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Value;

/**
 * 快照构建结果。无效目录不会携带部分快照。
 *
 * @author surezzzzzz
 */
@Value
public class RouteSnapshotBuildResult {

    /**
     * 校验结果。
     */
    RouteValidationResult validation;
    /**
     * 全量成功时的快照，否则为空。
     */
    RouteSnapshot snapshot;

    /**
     * 创建成功结果。
     */
    public static RouteSnapshotBuildResult accepted(RouteSnapshot snapshot) {
        return new RouteSnapshotBuildResult(RouteValidationResult.accepted(), snapshot);
    }

    /**
     * 创建拒绝结果。
     */
    public static RouteSnapshotBuildResult rejected(RouteValidationResult validation) {
        return new RouteSnapshotBuildResult(validation, null);
    }
}
