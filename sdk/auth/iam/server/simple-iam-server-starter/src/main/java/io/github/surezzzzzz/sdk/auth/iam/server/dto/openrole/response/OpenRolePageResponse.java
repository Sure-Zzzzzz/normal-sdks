package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 只包含已授权结果的开放接口分页。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class OpenRolePageResponse<T> {
    /**
     * 本页结果。
     */
    private final List<T> items;
    /**
     * 完整授权过滤后的数量。
     */
    private final long total;
    /**
     * 从一开始的页码。
     */
    private final int page;
    /**
     * 每页数量。
     */
    private final int size;
}
