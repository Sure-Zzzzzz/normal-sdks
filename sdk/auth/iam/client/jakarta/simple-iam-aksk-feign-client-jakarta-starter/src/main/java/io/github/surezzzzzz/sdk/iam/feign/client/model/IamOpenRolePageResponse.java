package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 受委托角色分页 wire（自持形态，页码 1 起）
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamOpenRolePageResponse {

    /**
     * 条目。
     */
    public java.util.List<IamOpenRoleResponse> items;

    /**
     * 总数。
     */
    public long total;

    /**
     * 页码（1 起）。
     */
    public int page;

    /**
     * 页大小。
     */
    public int size;
}
