package io.github.surezzzzzz.sdk.auth.data.permission.core.model;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collection;
import java.util.List;

/**
 * 数据授权文档。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DataGrantDocument {

    /**
     * 协议名称。
     */
    private final String protocol;
    /**
     * 协议版本。
     */
    private final String version;
    /**
     * 规范化后的授权项。
     */
    private final List<DataGrant> grants;

    /**
     * 创建数据授权文档。
     *
     * @param protocol 协议名称
     * @param version  协议版本
     * @param grants   授权项
     */
    public DataGrantDocument(String protocol, String version, Collection<DataGrant> grants) {
        if (!SimpleDataPermissionConstant.PROTOCOL.equals(protocol)) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_PROTOCOL, ErrorMessage.INVALID_PROTOCOL,
                    String.valueOf(protocol));
        }
        if (!SimpleDataPermissionConstant.VERSION.equals(version)) {
            throw DataPermissionValidationHelper.validation(ErrorCode.UNSUPPORTED_VERSION, ErrorMessage.UNSUPPORTED_VERSION,
                    String.valueOf(version));
        }
        this.protocol = protocol;
        this.version = version;
        this.grants = DataPermissionValidationHelper.normalizeObjects(grants, SimpleDataPermissionConstant.FIELD_GRANTS,
                SimpleDataPermissionConstant.MAX_GRANT_COUNT, DataPermissionValidationHelper.GRANT_COMPARATOR,
                ErrorCode.INVALID_DOCUMENT, ErrorMessage.INVALID_DOCUMENT);
        validateVersionOneOperators(this.grants);
    }

    private static void validateVersionOneOperators(Collection<DataGrant> grants) {
        for (DataGrant grant : grants) {
            for (DataConstraint constraint : grant.getConstraints()) {
                if (constraint.getOperator() != DataConstraintOperator.IN) {
                    throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_CONSTRAINT,
                            ErrorMessage.INVALID_CONSTRAINT,
                            String.format(SimpleDataPermissionConstant.DETAIL_UNSUPPORTED_CONSTRAINT_OPERATOR,
                                    constraint.getOperator().getCode()));
                }
            }
        }
    }
}
