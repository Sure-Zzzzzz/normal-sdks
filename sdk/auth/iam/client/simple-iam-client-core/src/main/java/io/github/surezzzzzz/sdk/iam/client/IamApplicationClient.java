package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamApplication;
import io.github.surezzzzzz.sdk.iam.client.model.IamSpringPage;

/**
 * IAM 可信应用目录 openapi 契约（1 方法，对位 server /iam/api/applications，1.3.7）。
 *
 * <p>消费方 AKP 需授 {@code iam:application:api} API 码与 {@code iam:application} DATA
 * 资源 read（all=true——注册表无部门归属，IN 范围不适用）；非 2xx 透传 Spring/Feign
 * 标准异常。分页为 Spring Page wire 形态（content/totalElements，页码 0 起），排序为
 * createdAt 倒序（新注册在前）。</p>
 *
 * @author surezzzzzz
 */
public interface IamApplicationClient {

    /**
     * 分页列出全部可信应用（全局目录，无用户维度）。
     *
     * @param page 页码（0 起）
     * @param size 页大小
     * @return 可信应用分页（目录三字段）
     */
    IamSpringPage<IamApplication> listApplications(Integer page, Integer size);
}
