package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * IAM 统计聚合（statistics 族响应模型，1.3.7）：与管理台同源口径。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamStatistics {

    /**
     * 用户总数。
     */
    long user;

    /**
     * 部门总数。
     */
    long department;

    /**
     * 协作组总数。
     */
    long userGroup;

    /**
     * 角色总数。
     */
    long role;

    /**
     * 权限总数。
     */
    long permission;

    /**
     * 可信应用总数。
     */
    long trustedApplication;

    /**
     * 在线会话数。
     */
    long activeSessions;

    /**
     * 今日登录用户数。
     */
    long todayLoggedInUsers;

    /**
     * 锁定用户数。
     */
    long lockedUsers;

    /**
     * 停用用户数。
     */
    long disabledUsers;

    /**
     * 未挂部门用户数。
     */
    long usersWithoutDepartment;
}
