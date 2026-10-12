package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IAM 统计聚合 wire 模型（1.0.2）：wire 为 {counts:{...},stats:{...}} 嵌套（与管理台同源）。
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamStatisticsResponse {

    /**
     * 治理规模计数段。
     */
    public Counts counts;

    /**
     * 运行时统计段。
     */
    public Stats stats;

    /**
     * 治理规模计数。
     */
    public static class Counts {
        /**
         * 用户总数。
         */
        public long user;
        /**
         * 部门总数。
         */
        public long department;
        /**
         * 协作组总数。
         */
        public long userGroup;
        /**
         * 角色总数。
         */
        public long role;
        /**
         * 权限总数。
         */
        public long permission;
        /**
         * 可信应用总数。
         */
        public long trustedApplication;
    }

    /**
     * 运行时统计。
     */
    public static class Stats {
        /**
         * 在线会话数。
         */
        public long activeSessions;
        /**
         * 今日登录用户数。
         */
        public long todayLoggedInUsers;
        /**
         * 锁定用户数。
         */
        public long lockedUsers;
        /**
         * 停用用户数。
         */
        public long disabledUsers;
        /**
         * 未挂部门用户数。
         */
        public long usersWithoutDepartment;
    }
}
