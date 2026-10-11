package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamPermission;

import java.util.List;

/**
 * IAM 权限清单 openapi 契约（1 方法，对位 server /iam/api/permissions，1.3.7）。
 *
 * <p>只读目录（对账/下拉）；码 {@code iam:permission:api}；无 DATA 面。</p>
 *
 * @author surezzzzzz
 */
public interface IamPermissionClient {

    /**
     * 分页列权限（keyword 过滤编码/名称；页码 1 起）。
     *
     * @param keyword 关键字（null=不过滤）
     * @param page    页码（1 起）
     * @param size    页大小
     * @return 权限清单
     */
    List<IamPermission> listPermissions(String keyword, Integer page, Integer size);
}
