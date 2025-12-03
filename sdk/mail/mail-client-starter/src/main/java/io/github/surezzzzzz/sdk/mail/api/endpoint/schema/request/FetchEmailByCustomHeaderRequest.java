package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request;

import lombok.*;

import javax.mail.Folder;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/13 18:25
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FetchEmailByCustomHeaderRequest {
    private int pageSize = 10; // 默认每页10封邮件
    private int pageNo = 1;
    private Folder folder;
    private String headerName;
    private String headerValue;
}