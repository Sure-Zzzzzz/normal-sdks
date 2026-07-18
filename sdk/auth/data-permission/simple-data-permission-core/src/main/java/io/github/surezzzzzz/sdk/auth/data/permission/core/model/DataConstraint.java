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
 * 数据授权约束。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DataConstraint {

    /**
     * 约束维度。
     */
    private final String dimension;
    /**
     * 约束操作符。
     */
    private final DataConstraintOperator operator;
    /**
     * 规范化后的约束值。
     */
    private final List<String> values;

    /**
     * 创建数据授权约束。
     *
     * @param dimension 约束维度
     * @param operator  约束操作符
     * @param values    约束值
     */
    public DataConstraint(String dimension, DataConstraintOperator operator, Collection<String> values) {
        this.dimension = DataPermissionValidationHelper.requireIdentifier(dimension,
                SimpleDataPermissionConstant.FIELD_DIMENSION, ErrorCode.INVALID_CONSTRAINT,
                ErrorMessage.INVALID_CONSTRAINT);
        if (operator == null) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_CONSTRAINT, ErrorMessage.INVALID_CONSTRAINT,
                    String.format(SimpleDataPermissionConstant.DETAIL_CANNOT_BE_NULL,
                            SimpleDataPermissionConstant.FIELD_OPERATOR));
        }
        this.operator = operator;
        this.values = DataPermissionValidationHelper.normalizeStrings(values, SimpleDataPermissionConstant.FIELD_VALUES,
                SimpleDataPermissionConstant.MAX_VALUE_COUNT, SimpleDataPermissionConstant.MAX_VALUE_CODE_POINT_COUNT,
                ErrorCode.INVALID_CONSTRAINT, ErrorMessage.INVALID_CONSTRAINT);
    }
}
