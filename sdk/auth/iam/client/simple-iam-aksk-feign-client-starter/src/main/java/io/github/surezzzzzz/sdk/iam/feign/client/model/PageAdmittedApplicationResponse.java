package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 页面准入应用详情 wire 模型（1.3.7）
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageAdmittedApplicationResponse {

    /**
     * 应用编码。
     */
    public String code;

    /**
     * 应用显示名（与门户侧边栏同源）。
     */
    public String displayName;

    /**
     * 应用描述（可空）。
     */
    public String description;
}
