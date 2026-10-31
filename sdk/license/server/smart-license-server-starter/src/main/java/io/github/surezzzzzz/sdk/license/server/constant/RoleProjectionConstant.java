package io.github.surezzzzzz.sdk.license.server.constant;

/** 专用角色联动的策略预算与对外固定错误说明。 */
public final class RoleProjectionConstant {
    public static final long PLAN_TTL_SECONDS = 24L * 3600L;
    public static final String MANUAL = "MANUAL";
    public static final String AUTO = "AUTO";
    public static final String MISSING_PORT = "角色协作端口未装配，无法核验实际清单";
    public static final String INVALID_BINDING = "客户组织或目标应用绑定未批准";
    public static final String INVALID_RULES = "须提供目标应用批准的精确 PAGE/API/DATA 规则快照";
    public static final String STALE_PLAN = "计划已失效，请回读环境绑定并生成最新清单";
    public static final String UNSAFE_ROLE = "专用角色存在漂移或未知写入，须人工核实后收口";
    public static final String INVALID_DIRECTORY = "客户组织目录不完整或父关系异常";
    public static final String EXECUTION_TOKEN_PATTERN = "[0-9a-f-]{36}";
    public static final String EXECUTION_IN_PROGRESS = "EXECUTION_IN_PROGRESS";

    private RoleProjectionConstant() { }
}
