package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * IAM 部门 wire 模型
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamDepartmentResponse {

    /**
     * 部门 ID。
     */
    public Long id;

    /**
     * 编码。
     */
    public String code;

    /**
     * 名称。
     */
    public String name;

    /**
     * 父部门。
     */
    public Long parentId;

    /**
     * 完整路径。
     */
    public String fullPath;

    /**
     * 排序号。
     */
    public Integer sortOrder;

    /**
     * 状态。
     */
    public Integer status;
}
