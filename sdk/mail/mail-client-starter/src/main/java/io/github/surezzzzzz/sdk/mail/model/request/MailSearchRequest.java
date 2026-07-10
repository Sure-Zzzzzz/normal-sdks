package io.github.surezzzzzz.sdk.mail.model.request;

import io.github.surezzzzzz.sdk.mail.constant.MailFlag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mail 搜索请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件夹
     */
    private String folder;

    /**
     * 主题关键字
     */
    private String subjectKeyword;

    /**
     * Header 名称
     */
    private String headerName;

    /**
     * Header 值
     */
    private String headerValue;

    /**
     * 邮件标志
     */
    private MailFlag mailFlag;

    /**
     * 分页请求
     */
    private MailPageRequest page;
}
