package io.github.surezzzzzz.sdk.mail.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Mail 分页结果
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailPageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    private int pageNo;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 扫描数量
     */
    private int scanned;

    /**
     * 匹配数量
     */
    private int matched;

    /**
     * 当前页数据
     */
    private List<MailReadResult> records;
}
