package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataAccessPlanRestrictionVerifier;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.model.IamOpenRoleActor;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.util.*;

/**
 * DATA 完整授权项进入查询和目标校验；未知维度或非法值失败关闭。
 *
 * @author surezzzzzz
 */
public final class IamOpenRoleDataAccessPlanHelper {
    private IamOpenRoleDataAccessPlanHelper() {
    }

    /**
     * 校验计划的全部条件，不能忽略不可执行的分支后放行。
     */
    public static void validate(DataAccessPlan plan, String... allowedDimensions) {
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) denied();
        Set<String> allowed = new HashSet<>(Arrays.asList(allowedDimensions));
        for (DataGrant grant : plan.getGrants()) {
            for (DataConstraint constraint : grant.getConstraints()) {
                if (constraint.getOperator() != DataConstraintOperator.IN || !allowed.contains(constraint.getDimension()))
                    denied();
                for (String value : constraint.getValues()) {
                    try {
                        if (SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID.equals(constraint.getDimension())) {
                            if (!IamOpenRoleProtocolHelper.uuid(value).equals(value)) denied();
                        } else {
                            Long id = IamOpenRoleProtocolHelper.positive(Long.valueOf(value));
                            if (!id.toString().equals(value)) denied();
                        }
                    } catch (RuntimeException exception) {
                        denied();
                    }
                }
            }
        }
    }

    /**
     * 单目标同时满足至少一条完整 grant，不拼接不同授权项。
     */
    public static void require(DataAccessPlan plan, Map<String, String> dimensions) {
        validate(plan, dimensions.keySet().toArray(new String[0]));
        if (!DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions)) denied();
    }

    /**
     * 数据库查询与 count 使用相同主体现有范围及 OR/AND 条件。
     */
    public static Specification<IamOpenRoleBindingEntity> roleSpecification(
            IamOpenRoleActor actor, DataAccessPlan plan, Long applicationId, Long rootDepartmentId, String externalId) {
        validate(plan, SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get(SimpleIamServerConstant.OPEN_ROLE_FIELD_OWNER_SOURCE_ID), actor.getSourceId()));
            predicates.add(builder.equal(root.get(SimpleIamServerConstant.OPEN_ROLE_FIELD_OWNER_SUBJECT_TYPE), actor.getSubjectType()));
            predicates.add(builder.equal(root.get(SimpleIamServerConstant.OPEN_ROLE_FIELD_OWNER_SUBJECT_ID), actor.getSubjectId()));
            if (applicationId != null)
                predicates.add(builder.equal(root.get(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID), applicationId));
            if (rootDepartmentId != null)
                predicates.add(builder.equal(root.get(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID), rootDepartmentId));
            if (externalId != null)
                predicates.add(builder.equal(root.get(SimpleIamServerConstant.OPEN_ROLE_FIELD_EXTERNAL_ID), externalId));
            if (plan.getOutcome() != DataAccessOutcome.ALLOW_ALL) {
                List<Predicate> grants = new ArrayList<>();
                for (DataGrant grant : plan.getGrants()) {
                    List<Predicate> constraints = new ArrayList<>();
                    for (DataConstraint constraint : grant.getConstraints()) {
                        List<Long> ids = new ArrayList<>();
                        for (String value : constraint.getValues()) ids.add(Long.valueOf(value));
                        constraints.add(root.get(constraint.getDimension()).in(ids));
                    }
                    grants.add(builder.and(constraints.toArray(new Predicate[0])));
                }
                predicates.add(builder.or(grants.toArray(new Predicate[0])));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 将同一委托记录的维度组合成一个完整目标。
     */
    public static Map<String, String> dimensions(IamOpenRoleBindingEntity binding, boolean includeRole, Long departmentId) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID, binding.getApplicationId().toString());
        dimensions.put(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID, binding.getRootDepartmentId().toString());
        if (includeRole)
            dimensions.put(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID, binding.getOpenRoleId());
        if (departmentId != null)
            dimensions.put(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID, departmentId.toString());
        return dimensions;
    }

    private static void denied() {
        throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_FORBIDDEN);
    }
}
