package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 组织目录（按授权根）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOrganizationDirectory {

    /**
     * 授权根部门 ID。
     */
    Long rootDepartmentId;

    /**
     * 目录是否完整可见（DATA 范围内）。
     */
    boolean complete;

    /**
     * 目录摘要。
     */
    String directoryDigest;

    /**
     * 部门列表。
     */
    java.util.List<IamOpenDirectoryDepartment> departments;
}
