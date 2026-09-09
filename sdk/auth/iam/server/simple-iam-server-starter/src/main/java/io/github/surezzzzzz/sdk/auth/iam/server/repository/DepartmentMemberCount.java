package io.github.surezzzzzz.sdk.auth.iam.server.repository;

/**
 * 部门直属成员计数投影
 *
 * @author surezzzzzz
 */
public interface DepartmentMemberCount {

    /**
     * 获取部门 ID
     *
     * @return 部门 ID
     */
    Long getDepartmentId();

    /**
     * 获取直属成员数
     *
     * @return 直属成员数
     */
    long getMemberCount();
}
