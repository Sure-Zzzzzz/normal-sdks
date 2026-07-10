package io.github.surezzzzzz.sdk.mail.model.result;

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
 * Mail 读取结果
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailReadResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Message-ID
     */
    private String messageId;

    /**
     * 主题
     */
    private String subject;

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
    private Map<String, List<String>> headers;

    /**
     * 是否已读
     */
    private boolean seen;

    /**
     * 接收时间
     */
    private Long receivedTime;
}
