package io.github.surezzzzzz.sdk.auth.collaboration.demo.service;

import io.github.surezzzzzz.sdk.auth.collaboration.demo.entity.OrderEntity;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DataAccessPlan 到 orders 查询条件的转换器。
 *
 * <p>语义：同一 grant 内约束按 AND，不同 grant 之间按 OR；缺失维度不限制；
 * 未知维度、非 IN 操作符或无法完整执行的计计划一律失败关闭。</p>
 *
 * @author surezzzzzz
 */
public final class OrderDataAccessPlanConverter {

    private static final Map<String, String> DIMENSION_COLUMNS = new HashMap<String, String>();

    static {
        DIMENSION_COLUMNS.put("tenantId", "tenantId");
        DIMENSION_COLUMNS.put("departmentId", "departmentId");
    }

    private OrderDataAccessPlanConverter() {
    }

    /**
     * 判断计划是否拒绝访问。
     *
     * @param plan 数据访问计划
     * @return 拒绝时返回 true
     */
    public static boolean isDenied(DataAccessPlan plan) {
        return plan == null || plan.getOutcome() == DataAccessOutcome.DENY;
    }

    /**
     * 将计划转换为订单查询条件；ALLOW_ALL 返回 null 表示不限制。
     *
     * @param plan 已验证的数据访问计划
     * @return JPA 查询条件
     */
    public static Specification<OrderEntity> toSpecification(DataAccessPlan plan) {
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return null;
        }
        List<Specification<OrderEntity>> grantSpecifications = new ArrayList<Specification<OrderEntity>>();
        for (DataGrant grant : plan.getGrants()) {
            grantSpecifications.add(toGrantSpecification(grant));
        }
        if (grantSpecifications.isEmpty()) {
            throw new IllegalStateException("受限访问计划缺少可执行授权项");
        }
        Specification<OrderEntity> combined = grantSpecifications.get(0);
        for (int i = 1; i < grantSpecifications.size(); i++) {
            final Specification<OrderEntity> left = combined;
            final Specification<OrderEntity> right = grantSpecifications.get(i);
            combined = (root, query, cb) -> cb.or(left.toPredicate(root, query, cb), right.toPredicate(root, query, cb));
        }
        return combined;
    }

    private static Specification<OrderEntity> toGrantSpecification(DataGrant grant) {
        List<String> columns = new ArrayList<String>();
        List<List<String>> valueGroups = new ArrayList<List<String>>();
        for (DataConstraint constraint : grant.getConstraints()) {
            if (constraint.getOperator() != DataConstraintOperator.IN) {
                throw new IllegalStateException("orders 边界只支持 IN 操作符，拒绝执行其他操作符");
            }
            String column = DIMENSION_COLUMNS.get(constraint.getDimension());
            if (column == null) {
                throw new IllegalStateException("orders 边界不支持授权维度 " + constraint.getDimension());
            }
            columns.add(column);
            valueGroups.add(constraint.getValues());
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("授权项缺少可执行约束");
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<Predicate>();
            for (int i = 0; i < columns.size(); i++) {
                predicates.add(root.<String>get(columns.get(i)).in(valueGroups.get(i)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构造单维度目标校验入参。
     *
     * @param tenantId     租户维度值
     * @param departmentId 部门维度值
     * @return 维度名到值的映射
     */
    public static Map<String, String> targetDimensions(String tenantId, String departmentId) {
        Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put("tenantId", tenantId);
        dimensions.put("departmentId", departmentId);
        return dimensions;
    }

    /**
     * 工具方法：便捷构造值列表。
     *
     * @param values 维度值
     * @return 值列表
     * @param <T>   值类型
     */
    public static <T> List<T> values(T... values) {
        return Arrays.asList(values);
    }
}
