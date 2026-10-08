package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 组织目录 wire
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamOrganizationDirectoryResponse {

    /**
     * 授权根。
     */
    public Long rootDepartmentId;

    /**
     * 完整性。
     */
    public boolean complete;

    /**
     * 摘要。
     */
    public String directoryDigest;

    /**
     * 部门列表。
     */
    public java.util.List<Department> departments;

    /**
     * 目录部门。
     */
    public static class Department {
        /**
         * 部门 ID。
         */
        public Long departmentId;

        /**
         * 编码。
         */
        public String code;

        /**
         * 父部门。
         */
        public Long parentId;

        /**
         * 名称。
         */
        public String name;

        /**
         * 状态。
         */
        public Integer status;

    }
}
