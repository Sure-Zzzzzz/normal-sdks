package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.response;

import lombok.Data;

import java.util.List;

/**
 * 用户 Excel 导入结果
 *
 * @author surezzzzzz
 */
@Data
public class UserImportResponse {

    /**
     * 非空数据行数
     */
    private int totalRows;

    /**
     * 成功创建数
     */
    private int createdRows;

    /**
     * 每行处理结果
     */
    private List<UserImportRowResponse> rows;
}
