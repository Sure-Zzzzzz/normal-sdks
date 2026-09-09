package io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理台仪表盘聚合响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class AdminDashboardResponse {

    private GovernanceCounts counts;

    private RuntimeStats stats;

    /**
     * 治理规模计数
     */
    @Data
    @AllArgsConstructor
    public static class GovernanceCounts {

        private long user;

        private long department;

        private long userGroup;

        private long role;

        private long permission;

        private long trustedApplication;
    }

    /**
     * 运行状态统计
     */
    @Data
    @AllArgsConstructor
    public static class RuntimeStats {

        /**
         * 在线会话数：status=1 且 expires_at > now，双实例共享 MySQL 天然全局
         */
        private long activeSessions;

        /**
         * 今日登录用户数：last_login_at >= 今日零点（按部署时区取零点）
         */
        private long todayLoggedInUsers;

        /**
         * 锁定中账号数：locked_until > now
         */
        private long lockedUsers;

        /**
         * 禁用账号数：status=0（治理缺口提示）
         */
        private long disabledUsers;

        /**
         * 未归属部门的用户数（治理缺口提示）
         */
        private long usersWithoutDepartment;
    }
}
