package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamStatistics;

/**
 * IAM 统计 openapi 契约（1 方法，对位 server /iam/api/statistics，1.3.7）。
 *
 * <p>只读聚合（外部监控/大屏）；码 {@code iam:dashboard:api}；无 DATA 面。
 * 登录明细不开放（遍历面属管理台深度职能）。</p>
 *
 * @author surezzzzzz
 */
public interface IamStatisticsClient {

    /**
     * 统计聚合（用户/应用/会话计数，与管理台同源）。
     *
     * @return 统计快照
     */
    IamStatistics getStatistics();
}
