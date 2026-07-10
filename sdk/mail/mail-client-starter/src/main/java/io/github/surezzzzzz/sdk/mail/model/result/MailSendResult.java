package io.github.surezzzzzz.sdk.mail.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mail 发送结果
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * Provider
     */
    private String provider;

    /**
     * Message-ID
     */
    private String messageId;

    /**
     * 发件人
     */
    private String from;

    /**
     * 收件人数
     */
    private int toCount;

    /**
     * 抄送人数
     */
    private int ccCount;

    /**
     * 密送人数
     */
    private int bccCount;

    /**
     * 附件数
     */
    private int attachmentCount;

    /**
     * 耗时
     */
    private Long tookMs;
}
