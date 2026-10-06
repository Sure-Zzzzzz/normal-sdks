package io.github.surezzzzzz.sdk.auth.iam.server.dto.openrole.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 同一数据库快照完整读取的部门子树；超预算不返回截断结果。
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class OpenOrganizationDirectoryResponse {
    /**
     * 批准的根节点。
     */
    private final Long rootDepartmentId;
    /**
     * 仅完整成功时为 true。
     */
    private final boolean complete;
    /**
     * 内容摘要，不是环境或组织版本。
     */
    private final String directoryDigest;
    /**
     * 根节点及当前后代。
     */
    private final List<OpenDepartmentResponse> departments;
}
