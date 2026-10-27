package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附件预签名申请。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentPresignRequest {

    /** 文件名。 */
    private String fileName;

    /** 内容类型。 */
    private String contentType;

    /** 预期字节数。 */
    private Long size;
}
