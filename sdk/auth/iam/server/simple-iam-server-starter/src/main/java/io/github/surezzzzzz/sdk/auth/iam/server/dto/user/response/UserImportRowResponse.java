package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import lombok.Data;

/**
 * 用户导入单行处理结果
 *
 * @author surezzzzzz
 */
@Data
public class UserImportRowResponse {

    /**
     * Excel 行号（从 2 开始）
     */
    private int rowNumber;

    /**
     * 行内用户名
     */
    private String username;

    /**
     * 是否创建成功
     */
    private boolean created;

    /**
     * 未创建时的可展示原因
     */
    private String message;
}
