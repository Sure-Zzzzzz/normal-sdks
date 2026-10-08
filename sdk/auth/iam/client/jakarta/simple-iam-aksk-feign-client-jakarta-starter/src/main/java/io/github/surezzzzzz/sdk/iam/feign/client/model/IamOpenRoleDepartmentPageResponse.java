package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 角色-部门挂载分页 wire（附整体 revision）
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamOpenRoleDepartmentPageResponse {

    /**
     * 条目。
     */
    public java.util.List<Item> items;

    /**
     * 总数。
     */
    public long total;

    /**
     * 页码。
     */
    public int page;

    /**
     * 页大小。
     */
    public int size;

    /**
     * 整体修订。
     */
    public long revision;

    /**
     * 挂载条目。
     */
    public static class Item {
        /**
         * 部门 ID。
         */
        public Long departmentId;

        /**
         * 在授权根内。
         */
        public boolean inCurrentRoot;

        /**
         * 挂载时间。
         */
        public String createdAt;

    }
}
