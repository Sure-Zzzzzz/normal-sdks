package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 组织成员定位 wire
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamOrganizationMemberResponse {

    /**
     * 公开主体。
     */
    public String subjectId;

    /**
     * 状态。
     */
    public Integer status;

    /**
     * 部门。
     */
    public Long departmentId;

    /**
     * 在授权根内。
     */
    public boolean inCurrentRoot;
}
