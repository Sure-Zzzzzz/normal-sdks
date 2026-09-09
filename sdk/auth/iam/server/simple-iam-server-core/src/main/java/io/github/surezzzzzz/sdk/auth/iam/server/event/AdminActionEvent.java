package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 管理面操作事件（实体增删改、状态变更、关系分配、密钥轮换、授权管理等）。
 *
 * <p>动作（{@link AdminActionType}）与目标类型（{@link AdminSubjectType}）正交组合，
 * 关联对象（如分配关系中的对端标识）放入 {@code detail} 自由文本，避免为每类关系
 * 扩展专用字段。系统自动操作（bootstrap 首建、部署恢复）{@code operator} 为空。
 *
 * <p>仅携带非敏感元数据；任何情况下不得携带密码、secret、token 原文。
 *
 * @author surezzzzzz
 */
@Getter
public class AdminActionEvent extends AbstractIamEvent {

    /**
     * 管理动作类型。
     */
    private final AdminActionType action;

    /**
     * 操作目标实体类型。
     */
    private final AdminSubjectType subjectType;

    /**
     * 目标实体标识（用户 ID / 角色 ID / clientId 等，统一字符串化）。
     */
    private final String subjectId;

    /**
     * 目标实体可读名（username / 角色编码 / 应用编码等）。
     */
    private final String subjectName;

    /**
     * 操作人（当前认证用户名）；系统自动操作为 null。
     */
    private final String operator;

    /**
     * 补充信息（关联对象标识、来源等自由文本，须脱敏）。
     */
    private final String detail;

    public AdminActionEvent(Object source, AdminActionType action, AdminSubjectType subjectType,
                            String subjectId, String subjectName, String operator, String detail) {
        super(source);
        this.action = action;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.subjectName = subjectName;
        this.operator = operator;
        this.detail = detail;
    }
}
