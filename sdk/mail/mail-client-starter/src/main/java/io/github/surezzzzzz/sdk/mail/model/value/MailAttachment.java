package io.github.surezzzzzz.sdk.mail.model.value;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mail 附件
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 文件大小
     */
    private Long size;

    /**
     * 本地路径
     */
    private String path;

    /**
     * 内容字节
     */
    private byte[] content;
}
