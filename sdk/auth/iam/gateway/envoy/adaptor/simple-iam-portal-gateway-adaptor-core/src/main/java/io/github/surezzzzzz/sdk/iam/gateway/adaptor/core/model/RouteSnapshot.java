package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 可整体替换的不可变路由快照。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class RouteSnapshot {

    /**
     * IAM Portal 目录版本。
     */
    long catalogRevision;
    /**
     * 参与摘要的策略版本。
     */
    String policyVersion;
    /**
     * 同一内容对应的稳定快照版本。
     */
    String snapshotVersion;
    /**
     * 脱敏目标摘要，供日志和事件关联。
     */
    String targetDigest;
    /**
     * 归一化路由。
     */
    @Singular
    List<TrustedApplicationRoute> routes;
}
