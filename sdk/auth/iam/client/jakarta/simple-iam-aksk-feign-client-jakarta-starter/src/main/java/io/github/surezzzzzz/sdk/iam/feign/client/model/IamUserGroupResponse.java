package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 协作组 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamUserGroupResponse {
    /**
     * 组 ID。
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
     * 状态。
     */
    public Integer status;
    /**
     * 创建时间。
     */
    public String createdAt;
    /**
     * 更新时间。
     */
    public String updatedAt;
}
