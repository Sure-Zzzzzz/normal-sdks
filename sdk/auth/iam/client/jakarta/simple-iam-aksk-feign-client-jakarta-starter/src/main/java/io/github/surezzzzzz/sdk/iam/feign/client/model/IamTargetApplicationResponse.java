package io.github.surezzzzzz.sdk.iam.feign.client.model;

/**
 * 目标应用 wire
 *
 * <p>Feign wire DTO：公开字段直配 Jackson，字段名逐字对位 server 契约。</p>
 *
 * @author surezzzzzz
 */
public class IamTargetApplicationResponse {

    /**
     * 应用 ID。
     */
    public Long applicationId;

    /**
     * 编码。
     */
    public String applicationCode;

    /**
     * 名称。
     */
    public String name;

    /**
     * 状态。
     */
    public Integer status;

    /**
     * 内置。
     */
    public boolean builtIn;
}
