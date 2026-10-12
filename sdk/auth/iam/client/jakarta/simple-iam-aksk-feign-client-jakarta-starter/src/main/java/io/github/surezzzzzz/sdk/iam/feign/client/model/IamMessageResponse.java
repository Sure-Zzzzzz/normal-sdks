package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 站内信 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamMessageResponse {
    /**
     * 标题。
     */
    public String title;
    /**
     * 内容。
     */
    public String content;
    /**
     * 发送时间。
     */
    public String createdAt;
    /**
     * 是否已读。
     */
    public boolean read;
}
