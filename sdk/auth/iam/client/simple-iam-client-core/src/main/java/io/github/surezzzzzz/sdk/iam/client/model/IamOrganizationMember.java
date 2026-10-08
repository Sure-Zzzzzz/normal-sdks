package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 组织目录成员定位
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOrganizationMember {

    /**
     * 用户公开主体。
     */
    String subjectId;

    /**
     * 用户状态。
     */
    Integer status;

    /**
     * 所在部门 ID。
     */
    Long departmentId;

    /**
     * 是否在当前授权根内。
     */
    boolean inCurrentRoot;
}
