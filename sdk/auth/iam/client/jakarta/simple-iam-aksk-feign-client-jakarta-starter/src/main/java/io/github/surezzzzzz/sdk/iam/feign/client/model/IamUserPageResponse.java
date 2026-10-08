package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 用户分页 wire（Spring Page 形态，页码 0 起）
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamUserPageResponse {

    /**
     * 当前页条目。
     */
    public java.util.List<IamUserResponse> content;

    /**
     * 总元素数。
     */
    public long totalElements;

    /**
     * 总页数。
     */
    public int totalPages;

    /**
     * 页码（0 起）。
     */
    public int number;

    /**
     * 页大小。
     */
    public int size;
}
