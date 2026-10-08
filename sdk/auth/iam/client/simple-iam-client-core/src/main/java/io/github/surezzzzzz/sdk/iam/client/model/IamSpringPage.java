package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * Spring Page wire 形态分页（users/departments 族；与受委托角色族的自持分页是两种契约形态）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamSpringPage<T> {

    /**
     * 当前页条目。
     */
    java.util.List<T> content;

    /**
     * 总元素数。
     */
    long totalElements;

    /**
     * 总页数。
     */
    int totalPages;

    /**
     * 当前页码（0 起）。
     */
    int number;

    /**
     * 页大小。
     */
    int size;
}
