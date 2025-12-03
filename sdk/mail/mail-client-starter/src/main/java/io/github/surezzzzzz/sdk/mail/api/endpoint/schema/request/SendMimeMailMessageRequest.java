package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request;

import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant.TextType;
import lombok.*;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/10 15:07
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SendMimeMailMessageRequest {

    private String from;
    @NotEmpty(message = "收件人不能为空")
    private String[] to;
    @NotBlank(message = "邮件主题不能为空")
    private String[] cc;
    private String[] bcc;
    private String subject;
    @NotBlank(message = "邮件内容不能为空")
    private String text;
    private String textType = TextType.TEXT.getType();
    private String[] filePaths;
    //自定义邮件头
    private Map<String, String> headers = new HashMap<>();

    // 设置自定义的邮件头
    public void addHeader(String headerName, String headerValue) {
        if (headerName != null && headerValue != null) {
            this.headers.put(headerName, headerValue);
        }
    }
}