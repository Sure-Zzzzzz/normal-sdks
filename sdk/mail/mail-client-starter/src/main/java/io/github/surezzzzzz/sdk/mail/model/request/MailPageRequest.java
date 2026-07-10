package io.github.surezzzzzz.sdk.mail.model.request;

import io.github.surezzzzzz.sdk.mail.constant.MailConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Mail 分页请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailPageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    @Builder.Default
    private int pageNo = MailConstant.DEFAULT_PAGE_NO;

    /**
     * 每页大小
     */
    @Builder.Default
    private int pageSize = MailConstant.DEFAULT_PAGE_SIZE;
}
