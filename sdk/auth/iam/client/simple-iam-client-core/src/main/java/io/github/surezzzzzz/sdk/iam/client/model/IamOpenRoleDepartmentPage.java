package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 受委托角色-部门挂载分页（同自持分页形态，附带整体 revision）
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamOpenRoleDepartmentPage {

    /**
     * 挂载条目。
     */
    java.util.List<IamOpenRoleDepartment> items;

    /**
     * 总数。
     */
    long total;

    /**
     * 页码（1 起）。
     */
    int page;

    /**
     * 页大小。
     */
    int size;

    /**
     * 角色当前修订版本。
     */
    long revision;
}
