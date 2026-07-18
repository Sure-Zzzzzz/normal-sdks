package io.github.surezzzzzz.sdk.auth.data.permission.core.model;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataPermissionValidationHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.*;

/**
 * 数据授权项。
 *
 * @author surezzzzzz
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DataGrant {

    private static final Comparator<DataConstraint> CONSTRAINT_COMPARATOR = new Comparator<DataConstraint>() {
        @Override
        public int compare(DataConstraint left, DataConstraint right) {
            return DataPermissionValidationHelper.UNICODE_CODE_POINT_COMPARATOR.compare(left.getDimension(),
                    right.getDimension());
        }
    };

    /**
     * 资源标识。
     */
    private final String resource;
    /**
     * 规范化后的动作。
     */
    private final List<String> actions;
    /**
     * 是否允许全部数据。
     */
    private final boolean all;
    /**
     * 规范化后的不可拆分约束。
     */
    private final List<DataConstraint> constraints;

    /**
     * 创建数据授权项。
     *
     * @param resource    资源标识
     * @param actions     动作集合
     * @param all         是否允许全部数据
     * @param constraints 约束集合
     */
    public DataGrant(String resource, Collection<String> actions, boolean all, Collection<DataConstraint> constraints) {
        this.resource = DataPermissionValidationHelper.requireIdentifier(resource,
                SimpleDataPermissionConstant.FIELD_RESOURCE, ErrorCode.INVALID_GRANT, ErrorMessage.INVALID_GRANT);
        this.actions = DataPermissionValidationHelper.normalizeStrings(actions, SimpleDataPermissionConstant.FIELD_ACTIONS,
                SimpleDataPermissionConstant.MAX_ACTION_COUNT, SimpleDataPermissionConstant.MAX_IDENTIFIER_CODE_POINT_COUNT,
                ErrorCode.INVALID_GRANT, ErrorMessage.INVALID_GRANT);
        this.all = all;
        this.constraints = normalizeConstraints(constraints, all);
    }

    private static List<DataConstraint> normalizeConstraints(Collection<DataConstraint> constraints, boolean all) {
        List<DataConstraint> normalizedConstraints = DataPermissionValidationHelper.requireCollection(constraints,
                SimpleDataPermissionConstant.FIELD_CONSTRAINTS, SimpleDataPermissionConstant.MAX_CONSTRAINT_COUNT,
                ErrorCode.INVALID_GRANT, ErrorMessage.INVALID_GRANT);
        if (all) {
            if (!normalizedConstraints.isEmpty()) {
                throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_GRANT, ErrorMessage.INVALID_GRANT,
                        SimpleDataPermissionConstant.DETAIL_ALL_GRANT_CANNOT_CONTAIN_CONSTRAINT);
            }
            return Collections.emptyList();
        }
        if (normalizedConstraints.isEmpty()) {
            throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_GRANT, ErrorMessage.INVALID_GRANT,
                    SimpleDataPermissionConstant.DETAIL_RESTRICTED_GRANT_MUST_CONTAIN_CONSTRAINT);
        }
        Collections.sort(normalizedConstraints, CONSTRAINT_COMPARATOR);
        Set<String> dimensions = new HashSet<String>();
        for (DataConstraint constraint : normalizedConstraints) {
            if (!dimensions.add(constraint.getDimension())) {
                throw DataPermissionValidationHelper.validation(ErrorCode.INVALID_GRANT, ErrorMessage.INVALID_GRANT,
                        String.format(SimpleDataPermissionConstant.DETAIL_DUPLICATE_CONSTRAINT_DIMENSION,
                                constraint.getDimension()));
            }
        }
        return Collections.unmodifiableList(normalizedConstraints);
    }
}
