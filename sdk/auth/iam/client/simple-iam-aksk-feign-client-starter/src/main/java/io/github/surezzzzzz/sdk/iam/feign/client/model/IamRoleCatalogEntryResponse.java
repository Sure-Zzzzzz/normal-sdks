package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 角色目录条目 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamRoleCatalogEntryResponse {
    /**
     * 角色 ID。
     */
    public Long id;
    /**
     * 编码。
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
     * 是否内置。
     */
    public Integer builtIn;
    /**
     * 创建时间。
     */
    public String createdAt;
    /**
     * 更新时间。
     */
    public String updatedAt;
}
