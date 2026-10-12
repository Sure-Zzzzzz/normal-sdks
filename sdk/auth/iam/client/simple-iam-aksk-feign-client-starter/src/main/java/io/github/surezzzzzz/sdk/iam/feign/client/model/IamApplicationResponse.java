package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * IAM 可信应用目录 wire 模型（1.3.7）
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamApplicationResponse {

    /**
     * 应用编码。
     */
    public String applicationCode;

    /**
     * 应用名称。
     */
    public String applicationName;

    /**
     * 状态（1=启用）。
     */
    public Integer status;
}

