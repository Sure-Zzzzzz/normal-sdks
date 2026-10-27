package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附件上传预签名响应。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentPresignResponse {

    /** 对象键。 */
    private String objectKey;

    /** 上传预签名 URL。 */
    private String uploadUrl;

    /** 上传者主体标识。 */
    private String uploadedBy;
}
