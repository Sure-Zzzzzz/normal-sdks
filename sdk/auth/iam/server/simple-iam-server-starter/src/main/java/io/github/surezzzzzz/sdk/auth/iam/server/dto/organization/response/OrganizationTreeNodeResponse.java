package io.github.surezzzzzz.sdk.auth.iam.server.dto.organization.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织树节点响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class OrganizationTreeNodeResponse {

    /**
     * 部门 ID
     */
    private Long id;

    /**
     * 部门编码
     */
    private String code;

    /**
     * 部门名称
     */
    private String name;

    /**
     * 父部门 ID
     */
    private Long parentId;

    /**
     * 部门状态
     */
    private Integer status;

    /**
     * 同级排序值
     */
    private Integer sortOrder;

    /**
     * 直属成员数，包含停用成员，不包含子部门成员
     */
    private long directMemberCount;

    /**
     * 下级部门节点
     */
    private List<OrganizationTreeNodeResponse> children;

    /**
     * 构造不含下级部门的组织树节点
     *
     * @param id                部门 ID
     * @param code              部门编码
     * @param name              部门名称
     * @param parentId          父部门 ID
     * @param status            部门状态
     * @param sortOrder         同级排序值
     * @param directMemberCount 直属成员数
     */
    public OrganizationTreeNodeResponse(Long id, String code, String name, Long parentId,
                                        Integer status, Integer sortOrder, long directMemberCount) {
        this(id, code, name, parentId, status, sortOrder, directMemberCount,
                new ArrayList<OrganizationTreeNodeResponse>());
    }
}
