package io.github.surezzzzzz.sdk.iam.client;

import io.github.surezzzzzz.sdk.iam.client.model.IamDepartment;

import java.util.List;

/**
 * IAM 部门族 openapi 契约（5 方法，对位 server /iam/api/departments）。
 *
 * <p>消费方 AKP 需授 {@code iam:department:api} API 码；非 2xx 透传标准异常。</p>
 *
 * @author surezzzzzz
 */
public interface IamDepartmentClient {

    /**
     * 部门列表。
     *
     * @return 部门列表（wire 为数组形态）
     */
    List<IamDepartment> listDepartments();

    /**
     * 部门详情。
     *
     * @param departmentId 部门 ID
     * @return 部门
     */
    IamDepartment getDepartment(Long departmentId);

    /**
     * 创建部门（201）。
     *
     * @param code             部门编码
     * @param name             部门名称
     * @param parentId         父部门（null=根）
     * @param sortOrder        排序号（null=默认）
     * @param status           状态（null=默认启用）
     * @param memberSubjectIds 初始成员公开主体列表（null=空）
     * @return 创建后的部门
     */
    IamDepartment createDepartment(String code, String name, Long parentId, Integer sortOrder, Integer status,
                                   List<String> memberSubjectIds);

    /**
     * 更新部门。
     *
     * @param departmentId 部门 ID
     * @param name         名称（null=不动）
     * @param parentId     父部门（null=不动）
     * @param sortOrder    排序号（null=不动）
     * @param status       状态（null=不动）
     * @return 更新后的部门
     */
    IamDepartment updateDepartment(Long departmentId, String name, Long parentId, Integer sortOrder,
                                   Integer status);

    /**
     * 删除部门（204）。
     *
     * @param departmentId 部门 ID
     */
    void deleteDepartment(Long departmentId);
}
