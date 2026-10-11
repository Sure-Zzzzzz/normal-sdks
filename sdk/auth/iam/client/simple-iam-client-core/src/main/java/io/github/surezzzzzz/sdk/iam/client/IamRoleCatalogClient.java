package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamRoleCatalogEntry;

import java.util.List;

/**
 * IAM 角色目录 openapi 契约（1 方法，对位 server /iam/api/role-catalog，1.3.7）。
 *
 * <p>只读目录（内置+自定义全量，对账/下拉）；码 {@code iam:role:api}；无 DATA 面。
 * 路径用 role-catalog 区隔受委托角色族（/iam/api/roles 历史在册）。</p>
 *
 * @author surezzzzzz
 */
public interface IamRoleCatalogClient {

    /**
     * 分页列角色（keyword 过滤编码/名称；页码 1 起）。
     *
     * @param keyword 关键字（null=不过滤）
     * @param page    页码（1 起）
     * @param size    页大小
     * @return 角色目录
     */
    List<IamRoleCatalogEntry> listRoles(String keyword, Integer page, Integer size);
}
