package io.github.surezzzzzz.sdk.mail.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mail 移动请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailMoveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 源文件夹
     */
    private String sourceFolder;

    /**
     * 目标文件夹
     */
    private String targetFolder;

    /**
     * Message-ID
     */
    private String messageId;

    /**
     * 是否立即清理删除标记
     */
    private boolean expunge;
}
