package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 目标应用申报的 DATA 资源、动作与约束维度。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamDataResourceDeclaration {

    /**
     * 应用内唯一资源标识。
     */
    String resource;

    /**
     * 可授权动作列表。
     */
    List<String> actions;

    /**
     * 可用约束维度列表。
     */
    List<String> dimensions;
}
