package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamPortalApplicationPage;

/**
 * IAM Portal 机器路由投影契约（对应 {@code /iam/api/portal/applications}）。
 *
 * <p>该接口与三字段的 {@link IamApplicationClient} 分离，避免已有目录消费者被
 * Portal 地址字段和版本语义隐式改变。调用方使用 AKP 读取连续分页，并以首页返回的
 * {@code catalogRevision} 约束后续分页。</p>
 *
 * @author surezzzzzz
 */
public interface IamPortalApplicationClient {

    /**
     * 读取一页 Portal 路由投影。
     *
     * @param page            页码（0 起，缺省为 0）
     * @param size            页大小（1..100，缺省为 20）
     * @param catalogRevision 续读时使用的目录版本（正数）；首页传 null
     * @return Portal 路由投影分页
     */
    IamPortalApplicationPage listApplications(Integer page, Integer size, Long catalogRevision);
}
