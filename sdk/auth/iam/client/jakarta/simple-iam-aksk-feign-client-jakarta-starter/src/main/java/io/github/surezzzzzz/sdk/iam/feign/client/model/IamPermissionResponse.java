package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 权限 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamPermissionResponse {
    /**
     * 权限编码。
     */
    public String code;
    /**
     * 名称。
     */
    public String name;
    /**
     * 描述。
     */
    public String description;
    /**
     * 类型（page/api/data）。
     */
    public String type;
    /**
     * 是否内置。
     */
    public Integer builtIn;
}
