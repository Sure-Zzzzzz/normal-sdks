package io.github.surezzzzzz.sdk.mail.model.request;

import io.github.surezzzzzz.sdk.mail.model.value.MailAttachment;
import io.github.surezzzzzz.sdk.mail.model.value.MailContent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Mail 发送请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Provider
     */
    private String provider;

    /**
     * 发件人
     */
    private String from;

    /**
     * 收件人
     */
    private List<String> to;

    /**
     * 抄送人
     */
    private List<String> cc;

    /**
     * 密送人
     */
    private List<String> bcc;

    /**
     * 主题
     */
    private String subject;

    /**
     * 内容
     */
    private MailContent content;

    /**
     * 附件
     */
    private List<MailAttachment> attachments;

    /**
     * Header
     */
    private Map<String, String> headers;
}
