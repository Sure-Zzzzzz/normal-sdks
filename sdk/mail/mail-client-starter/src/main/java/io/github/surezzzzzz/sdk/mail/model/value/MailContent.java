package io.github.surezzzzzz.sdk.mail.model.value;

import io.github.surezzzzzz.sdk.mail.constant.MailContentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mail 内容
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailContent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 内容类型
     */
    private MailContentType type;

    /**
     * 内容文本
     */
    private String text;
}
