package io.github.surezzzzzz.sdk.auth.data.permission.core.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentSource;

import java.util.Optional;

/**
 * 数据权限评估器。
 *
 * @author surezzzzzz
 */
public interface DataPermissionEvaluator {

    /**
     * 评估授权文档对资源动作的访问结果。
     *
     * @param document 已验证的授权文档
     * @param request  资源动作请求
     * @return 不可拆分子句组成的访问计划
     */
    DataAccessPlan evaluate(DataGrantDocument document, DataPermissionRequest request);

    /**
     * 评估可选授权文档对资源动作的访问结果。
     *
     * @param document 已验证的可选授权文档
     * @param request  资源动作请求
     * @return 没有授权文档时返回拒绝计划
     */
    default DataAccessPlan evaluate(Optional<DataGrantDocument> document, DataPermissionRequest request) {
        if (request == null) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT,
                    SimpleDataPermissionConstant.DETAIL_PERMISSION_REQUEST_CANNOT_BE_NULL);
        }
        if (document == null) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT,
                    SimpleDataPermissionConstant.DETAIL_DOCUMENT_CANNOT_BE_NULL);
        }
        return document.isPresent() ? evaluate(document.get(), request) : DataAccessPlan.deny();
    }

    /**
     * 从授权文档来源评估资源动作的访问结果。
     *
     * @param source  已验证授权文档来源
     * @param request 资源动作请求
     * @return 没有授权文档时返回拒绝计划
     */
    default DataAccessPlan evaluate(DataGrantDocumentSource source, DataPermissionRequest request) {
        if (request == null) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT,
                    SimpleDataPermissionConstant.DETAIL_PERMISSION_REQUEST_CANNOT_BE_NULL);
        }
        if (source == null) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT,
                    SimpleDataPermissionConstant.DETAIL_DOCUMENT_CANNOT_BE_NULL);
        }
        Optional<DataGrantDocument> document = source.currentDocument();
        if (document == null) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT,
                    SimpleDataPermissionConstant.DETAIL_DOCUMENT_CANNOT_BE_NULL);
        }
        return document.isPresent() ? evaluate(document.get(), request) : DataAccessPlan.deny();
    }
}
