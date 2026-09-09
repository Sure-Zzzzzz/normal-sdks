package io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest;

import lombok.Data;

import java.util.List;

/**
 * DATA 资源声明：应用权限清单申报的数据权限资源。
 *
 * <p>resource 为资源标识（如 iam:user）；actions 为该资源可授权的动作集；
 * dimensions 为该资源约束可用的维度集。角色授权规则模板中的 grant
 * 必须落在该声明范围内（resource / action / dimension 各自 ⊆）。
 *
 * @author surezzzzzz
 */
@Data
public class DataResourceDeclaration {

    /**
     * 资源标识（应用内唯一）
     */
    private String resource;

    /**
     * 可授权动作列表
     */
    private List<String> actions;

    /**
     * 可用约束维度列表
     */
    private List<String> dimensions;
}
