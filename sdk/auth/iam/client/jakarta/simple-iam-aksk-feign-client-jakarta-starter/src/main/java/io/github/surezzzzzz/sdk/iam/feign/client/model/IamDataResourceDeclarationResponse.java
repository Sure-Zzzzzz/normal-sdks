package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 权限清单中的 DATA 资源声明 wire。
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamDataResourceDeclarationResponse {

    /**
     * 应用内唯一资源标识。
     */
    public String resource;

    /**
     * 可授权动作列表。
     */
    public java.util.List<String> actions;

    /**
     * 可用约束维度列表。
     */
    public java.util.List<String> dimensions;
}
